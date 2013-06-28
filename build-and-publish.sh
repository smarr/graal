#!/bin/sh
GIT_REV=`git rev-parse HEAD | cut -c1-8`
if [ ! -e published/$GIT_REV ] ;
then
  echo Publishing gitrev: $GIT_REV
  ./mx.sh clean
  ./mx.sh --vm server build
  #./mx.sh trufflejar
  
  mkdir published/$GIT_REV
  rm -Rf published/latest
  mkdir published/latest
  
  touch published/latest/git-rev-$GIT_REV
  
  cp ./truffle.jar truffle-dsl-processor.jar published/$GIT_REV/
  cp ./truffle.jar truffle-dsl-processor.jar published/latest/
  
  chmod -R a+r published
  rsync -rvz --del published/* soft:public_html/downloads/truffle/
fi
