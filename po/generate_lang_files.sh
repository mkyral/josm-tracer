#!/usr/bin/env bash

cd ../../../i18n/
./i18n.pl --potfile=../plugins/tracer/po/tracer.po --basedir=../plugins/tracer/data/ ../plugins/tracer/po/cs.po

cd -
