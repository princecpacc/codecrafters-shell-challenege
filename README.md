Java Unix Shell
A Unix-like shell built in Java as part of the CodeCrafters Shell Challenge and Operating Systems coursework.

Overview
This project implements a custom command-line shell that mimics the behavior of traditional Unix shells. The goal is to understand how shells work internally while gaining hands-on experience with important Operating System concepts such as process creation, command execution, file descriptors, redirection, job control, and inter-process communication.

Features
Base Shell
Interactive REPL (Read-Eval-Print Loop)

Custom shell prompt

Built-in commands:

exit
echo
type
Invalid command handling

PATH-based executable lookup

External command execution with arguments

Navigation
pwd
cd using absolute paths
cd using relative paths
cd ~ for home directory navigation
Quoting & Parsing
Single quotes
Double quotes
Backslash escaping
Quoted executable execution
Robust command parsing
Redirection
Standard output redirection (>, 1>)
Standard error redirection (2>)
Output append (>>, 1>>)
Error append (2>>)
Background Jobs
Background execution using &
Job registration and tracking
Job listing
Process reaping
Job number recycling
Background output handling
Pipelines
Two-command pipelines
Multi-command pipelines
Built-in command pipelines
Inter-process communication using pipes
Operating System Concepts Covered
Process Creation
Process Management
Command Parsing
Environment Variables
PATH Resolution
File Descriptors
Standard Input / Output / Error
Redirection
Background Execution
Job Control
Process Reaping
Inter-Process Communication (IPC)
Pipelines
Project Structure
src/
├── Main.java
├── Shell.java
├── Builtins/
├── Parser/
├── Jobs/
├── Pipeline/
└── Utils/
Example Usage
$ echo Hello World
Hello World

$ pwd
/home/user

$ cd projects

$ ls > files.txt

$ sleep 5 &

$ cat data.txt | grep hello | wc -l
Tech Stack
Java
ProcessBuilder API
CodeCrafters Shell Challenge
Learning Outcomes
By building this shell, I gained practical experience with:

Shell architecture
Command execution flow
Process lifecycle management
Background job handling
File descriptor manipulation
Pipeline implementation
Core Operating System concepts
Acknowledgements
CodeCrafters Shell Challenge
Operating Systems Coursework
Java Documentation
Author
Developed as part of the Operating Systems Shell Assignment.

