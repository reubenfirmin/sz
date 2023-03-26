# sz - find what's eating your disk space

## About

This is an improved version of `du`, written with Kotlin Native. It's intended to quickly answer the question that most of us
probably use `du` for, i.e. "what the hell is using all my disk space"?

It contains the following improvements:
* It's much faster
* It skips virtual filesystems like /proc and /sys
* It (always, and by default) skips paths mounted on other devices from the starting path (du has this option, but it's not a well know param)
* It sorts the output by file size, high to low, even when formatting them to be human readable
* It (by default) only returns results for directories using at least 1% of all files under the path
* It won't complain about directories that it doesn't have permission to access; for example, running against / as a regular user will work, but the size will be less than if you run with sudo

## Running & Options

./sz dir

* dir - directory to scan

Optimization:
* --threads - how many threads to execute when recursing the tree (best option is likely a function of number of cores)

Formatting:
* -v - verbose mode; don't summarize 
* -vv - extra verbose mode; also including directories that are 0 size
* -h - format numbers for humans (1.1G, 2.45M, etc)
* -c - turn off ansi escape modes / colors when in human mode

Examples:

`sz /tmp --human`

`sz -v --threads 50 /tmp -h`

Note that if you pipe to less, by default the ansi escape codes will show, which will be ugly. So you can either do:

`sz -c / | expand | less`

or

`sz / | expand | less -r`

(Note that the expansions are necessary because less doesn't properly interpret tab characters.) 

## Status

Beta.

It works, output matches `du` (with differences noted above), and it is around 2x as fast as `du` even without 
the skipping optimizations. With those optimizations it can be at least 10x as fast. 

It is not deeply battle tested or proven to be correct, and so is currently unsuitable for distribution. Extensive
tests need to be created.

TODOs:

Improvement: option to roll up summaries by common directory.
Improvement: better handling of formatting options.

## Building

apt install gcc-multilib
./build.sh
sudo ./install.sh
