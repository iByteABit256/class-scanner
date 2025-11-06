# Class Scanner

A command-line tool that analyzes Java class field names and types recursively. Given a Java source file or compiled class file, it generates a JSON report containing all fields from the specified class down to classes that only contain primitive types or standard Java library types.

## What It Does

- Analyzes Java source files (`.java`) or compiled class files (`.class`)
- Recursively scans all field types and their dependencies
- Generates JSON output with both tree and flat representations
- Supports multithreaded analysis for better performance
- Automatically detects Maven projects and resolves dependencies

## Usage

```bash
class-scanner <file> [OPTIONS]
```

### Examples

```bash
# Analyze a Java source file
class-scanner MyClass.java

# Analyze with verbose output
class-scanner MyClass.java -v

# Save output to file
class-scanner MyClass.java -f analysis.json

# Use custom number of threads
class-scanner MyClass.java -t 8

# Full example with all options
class-scanner /path/to/MyClass.java -v -t 4 -f output.json
```

## Options

| Option              | Description                                             |
|---------------------|---------------------------------------------------------|
| `-v, --verbose`     | Enable verbose output with progress information         |
| `-f, --file FILE`   | Save output to file instead of stdout                   |
| `-t, --threads INT` | Number of threads to use (default: number of CPU cores) |
| `-h, --help`        | Show help message                                       |

## Installation (Windows)

### Prerequisites

- Java 11 or later
- Maven (for analyzing Java source files)

### Steps

1. **Download or build the JAR file**
   ```cmd
   gradlew shadowJar
   ```

2. **Run the installation script as Administrator**
   ```cmd
   install-cli.bat
   ```

3. **Restart your command prompt**

4. **Test the installation**
   ```cmd
   class-scanner --help
   ```

### Manual Installation

If the automatic installer doesn't work:

1. Create `C:\bin` directory
2. Copy `class-scanner-1.0-SNAPSHOT-all.jar` to `C:\bin\class-scanner.jar`
3. Create `C:\bin\class-scanner.bat` with:
   ```batch
   @echo off
   java -jar "C:\bin\class-scanner.jar" %*
   ```
4. Add `C:\bin` to your PATH environment variable

## Requirements

- **For .java files**: Maven must be installed and available in PATH
- **For .class files**: Only Java runtime required