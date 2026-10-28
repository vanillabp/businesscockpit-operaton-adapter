#!/usr/bin/env bash
#
# Writes the list of type renames into rewrite/vanillabp-operaton.yml.
#
# The VanillaBP recipe in that file renames every class of the Camunda 7 extension whose name
# contains 'Camunda7'. OpenRewrite wants one ChangeType entry per class, and the classes
# change with the Camunda 7 extension. So this script reads the classes from the sources and
# writes the entries between the two marker lines of the recipe. Everything outside the
# markers stays as it is.
#
# The classes of the Camunda 7 adapter which the extension uses are not in the sources, so
# they are not listed. The text rules of the recipe rename them, see the comment there.
#
# Only top-level types are listed: one per file, named like the file. A nested type is
# renamed by the text rule 'Camunda7' -> 'Operaton' of the same recipe, which runs after the
# type renames.
#
# Usage: bin/write-type-renames.sh <source tree> <recipe file>
#   <source tree>  the sources of the Camunda 7 extension, before the VanillaBP recipe ran
#   <recipe file>  rewrite/vanillabp-operaton.yml

set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <source tree> <recipe file>" >&2
  exit 2
fi
tree=$1
recipe=$2

begin='# BEGIN type renames, written by bin/write-type-renames.sh'
end='# END type renames'
if ! /usr/bin/grep -qF "$begin" "$recipe" || ! /usr/bin/grep -qF "$end" "$recipe"; then
  echo "$recipe has no marker lines '$begin' and '$end'." >&2
  exit 1
fi

entries=$(mktemp)
trap 'rm -f "$entries"' EXIT

# sorted by path, so the same sources always give the same file
find "$tree" -path '*/src/*' -name '*Camunda7*.java' -not -path '*/target/*' | LC_ALL=C sort |
  while read -r file; do
    package=$(sed -n 's/^package \([a-zA-Z0-9_.]*\);.*/\1/p' "$file" | head -1)
    if [ -z "$package" ]; then
      echo "$file has no package line." >&2
      exit 1
    fi
    old=$(basename "$file" .java)
    new=${old//Camunda7/Operaton}
    cat <<ENTRY
  - org.openrewrite.java.ChangeType:
      oldFullyQualifiedTypeName: ${package}.${old}
      newFullyQualifiedTypeName: ${package}.${new}
ENTRY
  done > "$entries"

awk -v begin="$begin" -v end="$end" -v entries="$entries" '
  $0 == "  " begin { print; while ((getline line < entries) > 0) print line; skipping = 1; next }
  $0 == "  " end { skipping = 0 }
  !skipping { print }
' "$recipe" > "$recipe.new"
mv "$recipe.new" "$recipe"
echo "$(/usr/bin/grep -c 'ChangeType:' "$recipe") type renames written into $recipe"
