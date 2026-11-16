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
#   After each load, the size of the database on disk is appended to ../log/db-size.txt
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

# Size on disk of the database just loaded, appended to ../log/db-size.txt.
# Measured right after the load: the next run removes the container and its volumes.
# Containers: the docker volumes of the container (the data directory). SQLite and DuckDB: the database file.
# Usage: record_db_size <db_type>
record_db_size() {
    dbtype="$1"
    case "$dbtype" in
        sqlite|duckdb)
            where="bin/mavendb.$dbtype"
            kib=$(du -sk "mavendb.$dbtype" | cut -f1)
            ;;
        *)
            case "$dbtype" in
                mongodb) container=mavendb-mongo ;;
                *)       container="mavendb-$dbtype" ;;
            esac
            where="$container volumes"
            kib=0
            for dir in $(sudo docker inspect -f '{{range .Mounts}}{{if eq .Type "volume"}}{{.Source}} {{end}}{{end}}' "$container"); do
                kib=$((kib + $(sudo du -sk "$dir" | cut -f1)))
            done
            # OpenSearch declares no volume: its data is in the container layer, next to the snapshots
            if [ "$dbtype" = "opensearch" ]; then
                where="$container:/usr/share/opensearch/data"
                kib=$(sudo docker exec "$container" du -sk /usr/share/opensearch/data | cut -f1)
            fi
            ;;
    esac
    echo "$(date '+%Y-%m-%d %T') $dbtype $kib KiB $(echo "$kib" | awk '{printf "%.1f GiB", $1 / 1048576}') $where" \
        | tee -a ../log/db-size.txt
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
    ./run.sh file:///home/amos/develop/mavencentral/repo.maven.apache.org/maven2/.index/ "$dbtype" || return 1
    record_db_size "$dbtype"
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
