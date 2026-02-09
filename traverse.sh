#!/bin/bash

# Define the source directory and output file
src_dir="src"
output_file="combined.java"

# Empty the output file if it exists
> "$output_file"

# Traverse the src directory and process each .java file
find "$src_dir" -type f -name "*.java" | while IFS= read -r file; do
    # Append the header with the filename
    echo "=== $file ===" >> "$output_file"
    # Append the content of the file
    cat "$file" >> "$output_file"
    # Add a newline for separation
    echo "" >> "$output_file"
done

