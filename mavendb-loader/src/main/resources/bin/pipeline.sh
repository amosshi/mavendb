#!/bin/sh
#
# Load the maven index into one or all databases.
# Each run stops all database containers, then refreshes only the one it loads.
#
# Usage:
#   ./pipeline.sh [ALL|mysql|mariadb|psql|mongodb|opensearch|sqlite|duckdb]
#
#   ALL      Run for every database type, one after the other (default)
#   <dbtype> Run for that database type only
#
#   Exits with code 1 when a load fails; ALL stops at the first failed database.
#
# Example:
#   ./pipeline.sh
#   ./pipeline.sh psql
#

DB_TYPES="mysql mariadb psql mongodb opensearch sqlite duckdb"

# Stop and remove every database container: the machine does not have enough RAM to run them all.
# Their volumes become unused, and are deleted by the volume prune of compose-<db_type>.sh.
stop_all_databases() {
    for compose_file in compose-mariadb.yml compose-mongodb.yml compose-mysql.yml compose-opensearch.yml compose-psql.yml; do
        sudo docker compose -f "$compose_file" down
    done
}

# Stop all databases, refresh only the given one, then load the maven index into it.
# Usage: run_pipeline <db_type>   (mysql, mariadb, psql, mongodb, opensearch, sqlite, duckdb)
run_pipeline() {
    dbtype="$1"

    cd ../docker
    pwd
    stop_all_databases

    # SQLite and DuckDB are local files, they have no container
    case "$dbtype" in
        sqlite|duckdb) ;;
        *)
            ./compose-"$dbtype".sh
            sleep 30
            ;;
    esac

    cd ../bin
    pwd
    ./run.sh file:///home/amos/develop/mavencentral/repo.maven.apache.org/maven2/.index/ "$dbtype"
}

target="${1:-ALL}"

case "$target" in
    ALL|all)
        for dbtype in $DB_TYPES; do
            # Stop at the first failed load: its database is incomplete, and a later run would stop it anyway
            if ! run_pipeline "$dbtype"; then
                echo "Pipeline stopped: the load into $dbtype failed"
                exit 1
            fi
        done
        ;;
    mysql|mariadb|psql|mongodb|opensearch|sqlite|duckdb)
        run_pipeline "$target" || exit 1
        ;;
    *)
        echo "Invalid database type: $target. Expected ALL, or one of: $DB_TYPES"
        exit 1
        ;;
esac
