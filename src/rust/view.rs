use std::collections::HashMap;

pub struct FormatOptions {
    pub human: bool,
    pub nosummary: bool,
    pub zeroes: bool,
    pub colors: bool
}

/**
 * Output a text report
 */
pub fn report(dir: String, results: HashMap<String, u64>, options: FormatOptions) {
    let mut result: Vec<_> = results.iter().map(|(a,b)|(a, b)).collect();
    result.sort_by(|a, b| b.1.cmp(&a.1));

    println!("{} files size: {}", dir, fmt(results.get(&dir).unwrap(), options.human, options.colors));
    let full_size: u64 = results.values().sum();
    println!("{} total size: {}", dir, fmt(&full_size, options.human, options.colors));

    let summary = !options.nosummary && !options.zeroes;
    let one_percent = (full_size as f64 / 100.0) as u64;

    if summary {
        println!("Entries that consume at least 1% of space in this path");
    }
    println!("---------------------------------------------------------");
    result.iter()
        .filter(|x| !summary || x.1 > &one_percent)
        .filter(|x| options.zeroes || x.1 > &0)
        .for_each(|x| {
            print!("{}", fmt(x.1, options.human, options.colors));
            print!("\t\t");
            println!("{}", x.0);
        });
}

fn fmt(size: &u64, human: bool, colors: bool) -> String {
    return if !human {
        size.to_string()
    } else {
        match size {
            s if s > &1_000_000_000 => format!("{}G", round2(size, 1_000_000_000))
                .color(colors, HIGHLIGHT_4, ansi_fg)
                .format(colors, ansi_bold),
            s if s > &1_000_000 => format!("{}M", round2(size, 1_000_000))
                .color(colors, HIGHLIGHT_4, ansi_fg),
            s if s > &1_000 => format!("{}K", round2(size, 1_000))
                .color(colors, HIGHLIGHT_2, ansi_fg),
            _ => size.to_string()
                .color(colors, HIGHLIGHT_1, ansi_fg)
        }
    }
}

fn round2(size: &u64, divisor: u32) -> f64 {
    ((*size as f64 / divisor as f64) * 100.0).round() / 100.0
}

fn ansi_fg(fg: i32, content: String) -> String {
    format!("\x1B[{}m{}\x1B[0m", fg, content)
}

fn ansi_bold(content: String) -> String {
    format!("\x1B[1m{}\x1B[0m", content)
}

trait AnsiExt {
    fn color(&self, colors: bool, color: i32, ansi_fn: fn(i32, String) -> String) -> String;
    fn format(&self, colors: bool, ansi_fn: fn(String) -> String) -> String;
}

impl AnsiExt for String {
    fn color(&self, colors: bool, color: i32, ansi_fn: fn(i32, String) -> String) -> String {
        if colors {
            ansi_fn(color, self.to_string())
        } else {
            self.to_string()
        }
    }

    fn format(&self, colors: bool, ansi_fn: fn(String) -> String) -> String {
        if colors {
            ansi_fn(self.to_string())
        } else {
            self.to_string()
        }
    }
}

const HIGHLIGHT_0: i32 = 36;
const HIGHLIGHT_1: i32 = 32;
const HIGHLIGHT_2: i32 = 35;
const HIGHLIGHT_3: i32 = 33;
const HIGHLIGHT_4: i32 = 31;
