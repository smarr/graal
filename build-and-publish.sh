#!/bin/sh
GIT_REV=`git rev-parse HEAD | cut -c1-8`
if [ ! -e published/$GIT_REV ] ;
then
  ./mx.sh build
  mkdir published/$GIT_REV
  rm published/latest/*
  
  touch published/latest/git-rev-$GIT_REV
  
  cp ./graal/com.oracle.truffle.api/com.oracle.truffle.api.jar \
     ./graal/com.oracle.truffle.api.codegen/com.oracle.truffle.api.codegen.jar \
     ./graal/com.oracle.truffle.codegen.processor/com.oracle.truffle.codegen.processor.jar \
    published/$GIT_REV/
  
  cp published/$GIT_REV/* published/latest/
  
  chmod -R a+r published
  rsync -rvz --del published/* soft:public_html/downloads/truffle/
fi
