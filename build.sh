#!/bin/bash

if ./gradlew assemble; then
	# TODO figure out why "untitled"
	cp build/bin/sz/releaseExecutable/sz.kexe ./sz
	cp build/bin/sz/debugExecutable/sz.kexe ./sz
else
	echo Build failed!
fi	
