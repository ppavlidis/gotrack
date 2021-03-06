#!/usr/bin/env python
from __future__ import with_statement, division
import logging
import logging.config
import tempfile
import shutil
from collections import defaultdict
import MySQLdb.cursors

from utility import query_yes_no, timeit, Password
import parsers
import gotrack as gtdb
from model import Ontology
from resources import Resources

# log = Log(sys.stdout)
logging.config.fileConfig('logging.conf', disable_existing_loggers=False)
logging.addLevelName(logging.WARNING, "\033[1;31m%s\033[1;0m" % logging.getLevelName(logging.WARNING))
logging.addLevelName(logging.ERROR, "\033[1;41m%s\033[1;0m" % logging.getLevelName(logging.ERROR))
LOG = logging.getLogger()


@timeit
def main(resource_directory=None, cron=False, no_download=False):
    """ Overview:
        Collect resources
        Display info on missing ones
        Ask - Download missing ones
        Ask - Insert new GO editions
        Ask - Insert new GOA editions
        Ask - Update sec_ac
        Ask - preprocess
    """

    # Connect to database
    gotrack = gtdb.GOTrack(cursorclass=MySQLdb.cursors.SSCursor, **CREDS)
    LOG.info("Connected to host: %s, db: %s, user: %s", CREDS['host'], CREDS['db'], CREDS['user'])

    # Collect current state of resources
    if resource_directory is None:
        resource_directory = tempfile.mkdtemp()
        LOG.warn("No resource directory specified. Creating temporary folder at %s", resource_directory)

    check_ftp = not no_download and (cron or query_yes_no("Check FTP sites for new data?"))
    res = Resources(resource_directory, gotrack.fetch_current_state(), check_ftp)

    # Display current state of resource directory, database, and ftp site
    LOG.info(res)

    # if cron, force download of accession history file
    res.sec_ac = None if cron else res.sec_ac

    # Deal with missing data
    if res.ftp_checked and res.is_missing_data():
        missing_cnt = len(res.missing_go)
        if missing_cnt:
            LOG.warn("Missing %s GO Versions from FTP", missing_cnt)
            if cron or query_yes_no("Download missing GO Versions?"):
                res.download_missing_go_data()

        missing_cnt = sum([len(goa_sp) for goa_sp in res.missing_goa.values()])
        if missing_cnt:
            LOG.warn("Missing %s GOA Editions from FTP", missing_cnt)
            if cron or query_yes_no("Download missing GOA Editions?"):
                res.download_missing_goa_data()

        if not res.sec_ac:
            LOG.warn("Missing secondary accession file (sec_ac.txt)")
            if cron or query_yes_no("Download missing secondary accession file?"):
                res.download_accession_history(skip_if_exists=not cron)

        LOG.info("Re-checking state of resource directory")
        res.populate_existing_files()
        res.populate_missing_data()
        LOG.info(res)

        if res.is_missing_data() or not (cron or query_yes_no("Continue with updates?")):
            return

    # Insert new GO data
    new_go = res.get_new_go()
    if new_go:
        LOG.info("New GO Versions ready to update: %s", len(new_go))
        if cron or query_yes_no("Update GO tables? (Affected tables: '{go_edition}', '{go_term}', "
                                "'{go_adjacency}', '{go_alternate}')".format(**gotrack.tables)):
            for go_date in sorted(new_go.keys()):
                go_file = new_go[go_date]
                LOG.info("Begin: %s", go_date.strftime('%Y-%m-%d'))
                ont = Ontology.from_file_data(go_date, go_file)
                gotrack.update_go_tables(ont)

        newest_go_date = max(new_go)
        LOG.info("Newest ontology available for GO definitions: %s", newest_go_date.strftime('%Y-%m-%d'))
        if cron or query_yes_no("Update GO definitions with this ontology? (Affected tables: '{go_definition}'"
                                .format(**gotrack.tables)):
            ont = Ontology.from_file_data(newest_go_date, new_go[newest_go_date])
            gotrack.update_current_go_definitions(ont)

    else:
        LOG.info("There are no new GO Versions available to update")

    # Insert new GOA data
    new_goa = res.get_new_goa()
    new_goa_cnt = sum([len(goa_sp) for goa_sp in new_goa.values()])
    if new_goa_cnt:
        LOG.info("New GOA Editions ready to update: %s", new_goa_cnt)
        if cron or query_yes_no("Update GOA tables? (Affected tables: '{edition}', "
                                "'{accession}', '{annotation}', '{synonyms}')".format(**gotrack.tables)):
            goa_skipped = []
            for species, goa_eds in new_goa.iteritems():
                LOG.info("Begin Species: %s", species)
                sp_id = res.sp_map[species]

                for edition in sorted(goa_eds.keys()):
                    LOG.info("Edition: %s", edition)
                    gpa_file, gpi_file = goa_eds[edition]

                    meta = parsers.check_goa_gpa(gpa_file)

                    if meta is None:
                        LOG.warn('Skipping edition %s for species %s', edition, species)
                        goa_skipped.append((edition,species))
                        continue

                    generated, go_version = meta

                    meta = parsers.check_goa_gpi(gpi_file)

                    if meta is None:
                        LOG.warn('Skipping edition %s for species %s', edition, species)
                        goa_skipped.append((edition,species))
                        continue

                    try:
                        gotrack.insert_annotations(sp_id, edition, generated, parsers.process_goa_gpi(gpi_file),
                                                   parsers.process_goa_gpa(gpa_file))
                    except Exception as inst:
                        LOG.warn('Skipping edition %s for species %s', edition, species)
                        goa_skipped.append((edition,species))
                        continue

            if goa_skipped:
                LOG.warn("%s GOA Editions failed: %s", len(goa_skipped), goa_skipped)
            else:
                LOG.info("All GOA Editions completed successfully.")


    else:
        LOG.info("There are no new GOA Editions available to update")

    if res.sec_ac is not None and (cron or query_yes_no("Update secondary accession table? (Affected tables: '{sec_ac}')"
                                                        .format(**gotrack.tables))):
        LOG.info("Updating secondary accession table")
        data = parsers.process_sec_ac(res.sec_ac)
        gotrack.update_secondary_accession_table(data)

    if gotrack.requires_proprocessing():
        if cron or query_yes_no("Pre-process required, continue? (Make sure new GO, GOA, and secondary "
                                "accession data has been imported (Affects tables: '{staging_pre}{pp_current_edition}', "
                                "'{staging_pre}{pp_accession_history}', '{staging_pre}{pp_edition_aggregates}', "
                                "'{staging_pre}{pp_go_annotation_counts}')"
                                .format(**gotrack.tables)):
            pre_process(gotrack)
            return True
        else:
            LOG.info("Pre-processing skipped")
    else:
        LOG.info("Pre-processing not required")

    return False


def cleanup(d, cron=False):
    if d is not None and (cron or query_yes_no("Delete temporary folder {0} ?".format(d))):
        shutil.rmtree(d)
    elif d is not None:
        LOG.info("Temporary Folder: {0}".format(d))

def pre_process(gotrack=None):
    # This part is too large to do simply in a single transaction
    # thus we process to staging tables and swap them with production
    # tables once everything is done

    LOG.info("Pre-processing database...")

    gotrack.create_staging_tables()

    LOG.info("Creating staged current editions table...")
    gotrack.stage_current_editions()

    LOG.info("Creating staged accession history table...")
    gotrack.stage_accession_history_table()

    LOG.info("Creating aggregate tables...")
    stage_aggregates(gotrack)

def stage_aggregates(gotrack=None):
    """Manually: update pp_edition_aggregates inner join (select species_id, edition,
        sum(cast( 1/mf as decimal(10,8) ))/gene_count avg_mf from (select species_id, edition,
        (gene_count - inferred_annotation_count) mf from pp_go_annotation_counts
        inner join pp_edition_aggregates using(species_id, edition)) as t1
        inner join pp_edition_aggregates using(species_id, edition)
        group by species_id, edition) as mftable using (species_id, edition)
        set avg_multifunctionality=avg_mf;"""
    if gotrack is None:
        gotrack = gtdb.GOTrack(cursorclass=MySQLdb.cursors.SSCursor, **CREDS)
        LOG.info("Connected to host: {host}, db: {db}, user: {user}".format(**CREDS))

    # Organize editions by GO Edition
    go_ed_to_sp_ed = defaultdict(list)
    for sp_id, ed, _, go_ed, _ in gotrack.fetch_editions():
        go_ed_to_sp_ed[go_ed].append((sp_id, ed))

    # In order to calculate jaccard similarity of terms over time
    # we need a reference edition. I have chosen the most current edition.
    # Collect and invert current editions for more memory efficient use of ontologies
    current_editions = defaultdict(list)
    for sp_id, ed, _, go_ed, _ in gotrack.fetch_staged_current_editions():
        current_editions[go_ed] += [[sp_id, ed]]

    # Cache terms sets per gene for most recent editions
    LOG.info("Caching term sets for genes in current editions")
    current_term_set_cache = {}
    for go_ed, eds in current_editions.iteritems():
        adjacency_list = gotrack.stream_adjacency_list(go_ed)
        ont = Ontology.from_adjacency("1900-01-01", adjacency_list)  
        for sp_id, ed in eds:
            LOG.info("Starting Species (%s), Edition (%s), GO Edition (%s)", sp_id, ed, go_ed)
            annotations = gotrack.stream_staged_annotations(sp_id, ed)
            _, direct_term_set_per_gene_id, term_set_per_gene_id, _, _ = aggregate_annotations(annotations, ont, sp_id, ed)
            current_term_set_cache[sp_id] = [direct_term_set_per_gene_id, term_set_per_gene_id]
            if len(term_set_per_gene_id) == 0 or len(direct_term_set_per_gene_id) == 0:
                LOG.error("No data for current edition, pre-process failed.")
                raise ValueError("No Data")
            LOG.info("Cached Term Sets: (%s), Direct: (%s)", len(term_set_per_gene_id), len(direct_term_set_per_gene_id))

    # Loops through all editions and calculate aggregate stats for each
    i = 0
    for go_ed, eds in sorted(go_ed_to_sp_ed.iteritems()):
        i += 1
        LOG.info("Starting Ontology %s: %s / %s", go_ed, i, len(go_ed_to_sp_ed))
        adjacency_list = gotrack.stream_adjacency_list(go_ed)
        ont = Ontology.from_adjacency("1900-01-01", adjacency_list)
        j = 0
        for sp_id, ed in eds:
            j += 1
            if j % 25 == 0:
                LOG.info("Editions: %s / %s", j, len(eds))
            caches = current_term_set_cache[sp_id]
            annotations = gotrack.stream_staged_annotations(sp_id, ed)
            ed_agg_data, term_counts = process_aggregates_for_edition(annotations, ont, sp_id, ed, caches)

            if ed_agg_data is not None:
                gotrack.stage_edition_aggregates([ed_agg_data])

            if term_counts is not None:
                gotrack.stage_term_counts(sp_id, ed, term_counts[0], term_counts[1])

        LOG.info("Editions: %s / %s", j, len(eds))


def push_to_production(gotrack=None):
    if gotrack is None:
        # Connect to database
        gotrack = gtdb.GOTrack(**CREDS)
        LOG.info("Connected to host: {host}, db: {db}, user: {user}".format(**CREDS))

    gotrack.push_staging_tables()

    LOG.info("Staging area has been pushed to production, a restart of GOTrack is now necessary")
    LOG.info("Remember to delete temporary old data tables if everything works")


def aggregate_annotations(all_annotations_stream, ont, sp_id, ed):

    # These are for computing the number of genes associated with each go term
    direct_counts_per_term = defaultdict(int)
    gene_id_set_per_term = defaultdict(set)

    # These are for computing the number of go terms associated with each gene
    term_set_per_gene_id = defaultdict(set)
    direct_term_set_per_gene_id = defaultdict(set)

    # Propagation Cache for performance purposes
    annotation_count = 0

    for gene_id, go_id in all_annotations_stream:
        term = ont.get_term(go_id)

        if term is not None:
            annotation_count += 1

            # Deal with direct counts
            direct_counts_per_term[term] += 1

            # Deal with inferred counts
            ancestors = ont.get_ancestors(term, True)

            for anc in ancestors:  # gene counts
                gene_id_set_per_term[anc].add(gene_id)

            term_set_per_gene_id[gene_id].update(ancestors)
            direct_term_set_per_gene_id[gene_id].add(term)

    return annotation_count, direct_term_set_per_gene_id, term_set_per_gene_id, direct_counts_per_term, gene_id_set_per_term


def process_aggregates_for_edition(all_annotations_stream, ont, sp_id, ed, caches):
    direct_term_set_per_gene_id_cache, term_set_per_gene_id_cache = caches
    annotation_count, direct_term_set_per_gene_id, term_set_per_gene_id, direct_counts_per_term, gene_id_set_per_term = aggregate_annotations(all_annotations_stream, ont, sp_id, ed)

    # Convert sets into counts
    inferred_counts_per_term = {t: len(s) for t, s in gene_id_set_per_term.iteritems()}

    total_gene_set_size = sum(len(s) for s in gene_id_set_per_term.itervalues())
    total_term_set_size = sum(len(s) for s in term_set_per_gene_id.itervalues())
    gene_count = len(term_set_per_gene_id)

    # write_total_time = time.time()

    # default return values
    retvals = [None, None]

    # Write aggregates
    if gene_count > 0:
        # Calculate average multifunctionality
        avg_mf = 0
        for t, c in inferred_counts_per_term.iteritems():
            if c < gene_count:
                avg_mf += 1.0 / (gene_count - c)

        avg_mf /= gene_count

        # Calculate average Jaccard similarity to current edition
        sum_direct_jaccard = 0
        sum_jaccard = 0
        # Start with direct terms
        for gene_id, s in direct_term_set_per_gene_id.iteritems():
            cached_s = direct_term_set_per_gene_id_cache[gene_id]
            sum_direct_jaccard += jaccard_similarity(s, cached_s)

        # Now inferred terms
        for gene_id, s in term_set_per_gene_id.iteritems():
            cached_s = term_set_per_gene_id_cache[gene_id]
            sum_jaccard += jaccard_similarity(s, cached_s)

        avg_direct_jaccard = sum_direct_jaccard / gene_count
        avg_jaccard = sum_jaccard / gene_count

        # edition aggregate data
        retvals[0] = [sp_id, ed, gene_count, annotation_count / gene_count, total_term_set_size / gene_count,
                       total_gene_set_size / len(gene_id_set_per_term), avg_mf, avg_direct_jaccard, avg_jaccard]


    else:
        LOG.warn("No Genes in Species ({0}), Edition ({1})".format(sp_id, ed))
    
    # Write Term Counts
    if total_gene_set_size > 0 or total_term_set_size > 0:
        retvals[1] = [direct_counts_per_term, inferred_counts_per_term]
    else:
        LOG.warn("No Annotations in Species ({0}), Edition ({1})".format(sp_id, ed))

    return retvals

    # write_total_time = time.time() - write_total_time

    # print "Total Write Time: {0}".format(write_total_time)

    # total_time = time.time() - total_time
    # print ""
    # print "Total Time: {0}".format(total_time)
    # print "Ancestor %: {0}".format(100 * ancestor_total_time / total_time)
    # print "Write %: {0}".format(100 * write_total_time / total_time)


def jaccard_similarity(s1, s2):
    if s1 is None or s2 is None:
        return None
    if len(s1) == 0 and len(s2) == 0:
        return 1.0
    if len(s1) == 0 or len(s2) == 0:
        return 0.0

    return len(s1.intersection(s2)) / len(s1.union(s2))

if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(description='Update GOTrack Database', add_help=False)

    parser.add_argument('--help', action='help', help='show this help message and exit')

    parser.add_argument('-h', dest='host', type=str, required=True, help='DB host')

    parser.add_argument('-u', dest='user', type=str, required=True, help='DB user')

    parser.add_argument('-d', dest='db', type=str, required=True, help='DB name')

    parser.add_argument('-p', action=Password, nargs='?', dest='password', required=True,
                        help='Ask for DB user password')

    parser.add_argument('--resources', dest='resources', type=str, required=True,
                        help='a folder holding resources to use in the update')

    group = parser.add_mutually_exclusive_group(required=True)

    group.add_argument('--push', dest='push', action='store_true',
                       help='Push Staging to Production, does not insert data')

    group.add_argument('--meta', dest='meta', action='store_true',
                       help='Display Connection and Table Information')

    group.add_argument('--update', dest='update', action='store_true',
                       help='Runs Update with options')

    group.add_argument('--update-push', dest='update_push', action='store_true',
                       help='Runs Update with options followed by update with --push')

    group.add_argument('--process', dest='process', action='store_true',
                       help='Pre-processes the database.')

    parser.add_argument('--cron', dest='cron', action='store_true',
                        help='No interactivity mode')
    parser.add_argument('--no-downloads', dest='dl', action='store_true',
                        help='Prevent all downloads')

    args = parser.parse_args()

    CREDS = {'host': args.host,
             'user': args.user,
             'passwd': args.password,
             'db': args.db
             }

    if args.meta:
        LOG.info("Host: {host}, db: {db}, user: {user}".format(**CREDS))
        gotrack_db = gtdb.GOTrack(**CREDS)
        LOG.info('Tables: \n%s', "\n".join(sorted([str(table) for table in gotrack_db.tables.iteritems()])))
    elif args.update_push:
        # Update followed by push to production
        preprocessed = main(args.resources, args.cron, args.dl)
        if preprocessed:
            if args.cron or query_yes_no("Push staging to production?"):
                push_to_production()
            else:
                LOG.info("Push to production skipped")
        else:
            LOG.info("Push to production not required: Database was not pre-processed.")
    elif args.push:
        # Push Staging to Production
        push_to_production()
    elif args.process:
        # Update aggregate tables
        gotrack_db = gtdb.GOTrack(**CREDS)
        if gotrack_db.requires_proprocessing() or \
                query_yes_no("Database does not require pre-processing, continue anyways?"):
            pre_process(gotrack_db)
        else:
            LOG.info("Pre-processing skipped")
    elif args.update:
        # Update database
        main(args.resources, args.cron, args.dl)
    else:
        LOG.error("No goal supplied.")
