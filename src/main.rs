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

#[derive(Parser, Debug)]
// TODO for compatibility with du we turn off -h; not sure how to reenable help message
#[command(disable_help_flag(true))]
struct MyArgs {
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

/**
 * NOTE - this version is rudimentary / hacky. I'm starting to learn rust by building the
 * equivalent of the working (and more polished) kotlin native implementation.
 */
fn main() {
    let args = MyArgs::parse();

    println!("{} {} {} {} {} {}", args.human, args.threads, args.nosummary, args.zeroes, args.nocolors, args.dir);
    let blacklist = HashSet::from(["/proc", "/sys"]);

    let dir = args.dir;
    let all_results = scan_path(&dir, 10, blacklist);
    let mut result: Vec<_> = all_results.iter().collect();
    result.sort_by(|a, b| b.1.cmp(a.1));

    for x in result.iter().take(10) {
        println!("{}\t\t{}", x.1, x.0)
    }
}

/**
 * Iterate a path and its subdirectories, collecting the size of each directory by summing files
 * within it.
 */
fn scan_path(dir: &String, threads: usize, blacklist: HashSet<&str>) -> HashMap<String, u64> {
    // set up a channel to receive results back from threads
    let (tx, rx) = std::sync::mpsc::channel();

    let pool = ThreadPool::new(threads);

    // TODO I don't love this algorithm. We run until we've received/handled the same number of
    // results as we've submitted; this is because I can't figure out how to check the pool to see
    // if anything is _actually_ running (vs than just being an active thread). Maybe it's fine.
    let mut job = 0;
    let mut found = 0;
    let mut results = HashMap::new();

    job += 1;
    submit(PathBuf::from(dir), &pool, tx.clone());

    while job > found {
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
                            // println!("{} {} {}", subdisplay, job, found);
                            job += 1;
                            submit(subpath, &pool, tx.clone());
                        } else {
                            // skipped, it's not a real filesystem
                            // println!("Skipping {}", subdisplay)
                        }
                    }
                },
                Err(_) => { /* sshh */}
            }
            found += 1;
        }
        yield_now()
    }
    results
}

/**
 * Submits a directory iteration to the worker pool.
 */
fn submit(path: PathBuf, pool: &ThreadPool, tx: Sender<Result<DirMetadata, Box<dyn Error + Send + Sync>>>) {
    pool.execute (move || {
        let result = process_directory(&path);
        tx.send(result).expect("Couldn't send!");
    });
}

/**
 * Sum the sizes of files in this directory, and collect any direct subpaths.
 */
fn process_directory(dir_path: &PathBuf) -> Result<DirMetadata, Box<dyn Error + Send + Sync>> {
    let metadata = fs::metadata(dir_path)?;
    let device = metadata.dev();
    if !metadata.is_dir() {
        return Result::Err(Box::new(MyError("Not a dir".into())))
    }

    let mut result = DirMetadata {
        path: dir_path.clone(),
        size: 0,
        paths: Vec::new()
    };

    let mut size = 0;

    for entry in fs::read_dir(dir_path)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_symlink() {
            continue
        // TODO avoid calling metadata twice on each dir!
        } else if path.is_dir() && path.metadata()?.dev() == device {
            result.paths.push(path);
        } else {
            size += match entry.metadata() {
                Ok(size) => size.len(),
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