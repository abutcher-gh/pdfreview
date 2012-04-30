#/usr/bin/env bash

if [ $# -lt 2 ]
then
   echo >&2
   echo >&2 "Usage: apply.sh confluence-flyingpdf-plugin-?.jar doctheme-?.jar"
   echo >&2
   echo >&2 "Outputs:"
   echo >&2 "   confluence-flyingpdf-plugin-?-pdfreview.jar"
   echo >&2 "   doctheme-?-pdfreview.jar"
   echo >&2
   echo >&2 "Order of the arguments IS NOT important."
   echo >&2
   exit 20
fi

THIS_DIR=$(dirname $0)

if [[ $1 = *flying* ]]
then
   FLYINGPDF_JAR=$1
   DOCTHEME_JAR=$2
else
   DOCTHEME_JAR=$1
   FLYINGPDF_JAR=$2
fi

FLYINGPDF=${FLYINGPDF_JAR%.jar}
DOCTHEME=${DOCTHEME_JAR%.jar}
FLYINGPDF=${FLYINGPDF##*/}
DOCTHEME=${DOCTHEME##*/}
FLYINGPDF=${FLYINGPDF##*\\}
DOCTHEME=${DOCTHEME##*\\}

rm -rf $FLYINGPDF && mkdir -p $FLYINGPDF && unzip -o $FLYINGPDF_JAR -d $FLYINGPDF || exit $?
rm -rf $DOCTHEME && mkdir -p $DOCTHEME && unzip -o $DOCTHEME_JAR -d $DOCTHEME || exit $?

FLYINGPDF_VERSION=${FLYINGPDF##*-}

cat $THIS_DIR/flyingpdfreview.diff | sed "s/__PATCH_VERSION/$FLYINGPDF_VERSION/" |
   (cd $FLYINGPDF && patch -p1) || exit $?

cat $THIS_DIR/doctheme.diff | (cd $DOCTHEME && patch -p1) || exit $?


(cd $FLYINGPDF && zip -r ../$FLYINGPDF-pdfreview.jar *) || exit $?
(cd $DOCTHEME && zip -r ../$DOCTHEME-pdfreview.jar *) || exit $?
