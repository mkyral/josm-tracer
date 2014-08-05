find ../src/org/openstreetmap/josm/plugins/tracer/  -name "*.java" -print >files_list
xgettext --files-from=files_list -d tracer --from-code=UTF-8 -k -ktrc:1c,2 -kmarktrc:1c,2 -ktr -kmarktr -ktrn:1,2 -ktrnc:1c,2,3

msgmerge -U cs.po tracer.po
