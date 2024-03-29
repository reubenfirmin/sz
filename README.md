# sz - find what's eating your disk space

## About

This is an improved version of `du`, with implementations in Kotlin Native, Rust, and Go. It's intended to quickly 
answer the question that most of us probably use `du` for, i.e. "what the hell is using all my disk space"?

It contains the following improvements:
* It's much faster
* It skips virtual filesystems like /proc and /sys
* It (always, and by default) skips paths mounted on other devices from the starting path (du has this option, but it's not a well known param)
* It's formats numbers as human readable by default
* It sorts the output by file size, high to low, even when formatting them to be human readable
* It (by default) only returns results for directories using at least 1% of all files under the path
* It won't complain about directories that it doesn't have permission to access; for example, running against / as a regular user will work, but the size will be less than if you run with sudo

## Demo

![demo](./demo.gif)

## Running & Options

./sz dir

* dir - directory to scan

Optimization:
* --threads - how many threads to execute when recursing the tree (best option is likely a function of number of cores)

Formatting:
* -v - verbose mode; don't summarize 
* -V - extra verbose mode; also including directories that are 0 size
* -r - format numbers for machine consumption rather than for humans (1053433 vs 1.05M)
* -c - turn off ansi escape modes / colors when in human mode

Examples:

`sz /tmp --raw`

`sz -v --threads 50 /tmp`

Note that if you pipe to less, by default the ansi escape codes will show, which will be ugly. So you can either do:

`sz -c / | expand | less`

or

`sz / | expand | less -r`

(Note that the expansions are necessary because less doesn't properly interpret tab characters.) 

## Status

Beta.

It works, and it (the Kotlin version) is around 2x as fast as `du` even without the skipping optimizations. With those optimizations it can be at least 10x as fast. 

It is not deeply battle tested or proven to be correct, and so is currently unsuitable for distribution. Extensive
tests need to be created.

TODOs:

Possible bug: need to understand what `du` does differently; both versions of `sz` report identical numbers, which are consistently a bit higher than `du`'s; manual checks of directories that I've done with `ls` also agree with `sz`. Is `du` wrong or just doing something subtly different? TBD.  

Improvement: option to roll up summaries by common directory.

Improvement: better handling of formatting options.

(Not much of a) Challenge: for kicks, write a Kotlin/JVM version. 

Challenge: write a TUI in either Go or Rust, or maybe both.

## Building

```
Install rust: https://www.rust-lang.org/tools/install 

apt install gcc-multilib golang
./build.sh
sudo ./install.sh
```

## Rust vs Kotlin Native vs Go

I wrote the Kotlin Native version first, because it's closest to what I'm familiar with (although it's still pretty 
foreign). After getting it working, I was curious how close to the metal JetBrains had managed to get, and so hacked
up the Rust version for comparison. The algorithms in both verions are currently identical, with minor differences in
the thread pools; Rust has one, whereas I had to write one for Kotlin Native.

I then started working on the Golang version (which is currently still in progress), which currently uses Goroutines rather than OS threads. 
It also doesn't currently use a worker pool, and this I think is why it's slightly slower than the Rust version (but still
faster than Kotlin).

