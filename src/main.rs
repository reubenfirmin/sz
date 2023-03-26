use std::fs;
use std::path::PathBuf;

fn main() {
    println!("Hello world");
    let args: Vec<String> = std::env::args().collect();

    if args.len() < 2 {
        println!("Usage: {} <directory>", args[0]);
        return;
    }

    let root_dir = PathBuf::from(&args[1]);

    let total_size = get_directory_size(&root_dir).unwrap_or_else(|err| {
        eprintln!("Error: {}", err);
        std::process::exit(1);
    });

    println!("Total size: {} bytes", total_size);
}

fn get_directory_size(dir_path: &PathBuf) -> Result<u64, String> {
    let metadata = fs::metadata(dir_path).map_err(|err| format!("{}", err))?;
    if !metadata.is_dir() {
        return Ok(metadata.len());
    }

    let mut size = metadata.len();

    for entry in fs::read_dir(dir_path).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        if path.is_dir() {
            size += match get_directory_size(&path) {
                Ok(size) => size,
                Err(_) => 0
            }
        } else {
            size += match entry.metadata() {
                Ok(size) => size.len(),
                Err(_) => 0
            }
        }
    }

    Ok(size)

}