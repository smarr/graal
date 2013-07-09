#!/bin/sh
GIT_REV=`git rev-parse HEAD | cut -c1-8`
if [ ! -e published/$GIT_REV ] ;
then
  ./mx.sh build
  mkdir published/$GIT_REV
  rm published/latest/*
  
  touch published/latest/git-rev-$GIT_REV
  
  cp ./graal/com.oracle.truffle.api/com.oracle.truffle.api.jar \
     ./graal/com.oracle.truffle.api.dsl/com.oracle.truffle.api.dsl.jar \
     ./graal/com.oracle.truffle.dsl.processor/com.oracle.truffle.dsl.processor.jar \
    published/$GIT_REV/
  
  cp published/$GIT_REV/* published/latest/
  
  chmod -R a+r published
  rsync -rvz --del published/* soft:public_html/downloads/truffle/
fi
