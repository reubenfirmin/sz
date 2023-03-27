mod view;

use std::error::Error;
use std::{fmt, fs};
use std::collections::HashMap;
use std::collections::HashSet;
use std::path::PathBuf;
use std::thread::yield_now;
use std::sync::mpsc::Sender;
use std::os::unix::fs::MetadataExt;
use threadpool::ThreadPool;
use clap::Parser;
use crate::view::report;
use crate::view::FormatOptions;

/**
 * NOTE - I ported the kotlin version to rust to start picking up the language. This is extremely
 * likely to not be idiomatic. (However, it works more or less identically.)
 */

/**
 * Command line args
 */
#[derive(Parser, Debug)]
// TODO for compatibility with du we turn off -h; not sure how to reenable help message
#[command(disable_help_flag(true))]
struct Args {
    #[arg(short, long)]
    human: bool,
    #[arg(short, long, default_value_t = 50)]
    threads: i32,
    #[arg(short='v', long)]
    nosummary: bool,
    #[arg(short='V', long)]
    zeroes: bool,
    #[arg(short='c', long)]
    nocolors: bool,
    dir: String
}

fn main() {
    let args = Args::parse();
    let dir = args.dir;
    let all_results = scan_path(&dir, 10, HashSet::from(["/proc", "/sys"]));
    report(dir, all_results, FormatOptions {
        human: args.human,
        nosummary: args.nosummary,
        zeroes: args.zeroes,
        colors: !args.nocolors
    });
}

/**
 * Iterate a path and its subdirectories, collecting the size of each directory by summing files
 * within it.
 */
fn scan_path(dir: &String, threads: usize, blacklist: HashSet<&str>) -> HashMap<String, u64> {
    // set up a channel to receive results back from threads
    let (tx, rx) = std::sync::mpsc::channel();

    let pool = ThreadPool::new(threads);

    // Run until we've received/handled the same number of results as we've submitted. Ideal world
    // we could get more state from the threadpool and not have to track this.
    let mut pending = 0;
    let mut results = HashMap::new();

    let path = PathBuf::from(dir);
    let device = fs::metadata(&path).expect(&format!("Cannot find path at {}", dir)).dev();

    pending += 1;
    submit(path, device, &pool, tx.clone());

    while pending > 0 {
        // pick results off the receiving channel
        let mut iter = rx.try_iter();
        while let Some(result) = iter.next() {
            match result {
                Ok(it) => {
                    let displayed = it.path.display().to_string();
                    results.insert(displayed,it.size);
                    for subpath in it.paths {
                        let subdisplay = subpath.display().to_string();
                        if !blacklist.contains(&*subdisplay) {
                            pending += 1;
                            submit(subpath, device, &pool, tx.clone());
                        }
                    }
                },
                Err(_) => {
                    // we suppress errors because we don't care about folders we don't have
                    // access to. TODO add flag to show these
                }
            }
            pending -= 1;
        }
        yield_now()
    }
    results
}

/**
 * Submits a directory iteration to the worker pool.
 */
fn submit(path: PathBuf, device: u64, pool: &ThreadPool, tx: Sender<Result<DirMetadata, Box<dyn Error + Send + Sync>>>) {
    pool.execute (move || {
        let result = process_directory(&path, device);
        tx.send(result).expect("Couldn't send!");
    });
}

/**
 * Sum the sizes of files in this directory, and collect any direct subpaths.
 */
fn process_directory(dir_path: &PathBuf, device: u64) -> Result<DirMetadata, Box<dyn Error + Send + Sync>> {
    let mut result = DirMetadata {
        path: dir_path.clone(),
        size: 0,
        paths: Vec::new()
    };

    let mut size = 0;

    for entry in fs::read_dir(dir_path)? {
        let subpath = entry?.path();

        if subpath.is_symlink() {
            continue
        // TODO avoid calling metadata twice on each dir!
        } else if subpath.is_dir() && subpath.metadata()?.dev() == device {
            result.paths.push(subpath);
        } else {
            size += match subpath.metadata() {
                Ok(metadata) => metadata.len(),
                Err(_) => 0
            }
        }
    }

    result.size = size;
    Ok(result)
}

// TODO is there a generic error I can just use instead of rolling my own?
#[derive(Debug)]
struct MyError(String);

impl fmt::Display for MyError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "There is an error: {}", self.0)
    }
}

impl Error for MyError{}

struct DirMetadata {
    path: PathBuf,
    paths: Vec<PathBuf>,
    size: u64
}