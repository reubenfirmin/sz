#!/bin/bash

echo BUILDING KOTLIN

if ./gradlew assemble; then
	cp build/bin/sz/releaseExecutable/sz.kexe ./sz_kn
else
	echo Kotlin build failed!
fi	

echo BUILDING RUST

if cargo build --release; then
	cp target/release/sz ./sz_rs
else
	echo Rust build failed!
fi

echo BUILDING GO

if go build -o sz_go src/golang/sz.go; then
	echo Success!
else
	echo Go build failed!
fi
