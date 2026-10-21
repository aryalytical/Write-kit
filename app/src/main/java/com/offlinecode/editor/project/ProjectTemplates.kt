package com.offlinecode.editor.project

class ProjectTemplate(val id: String, val title: String, val files: Map<String, String>)

object ProjectTemplates {
    private val web = ProjectTemplate(
        "web", "HTML / CSS / JavaScript",
        mapOf(
            "index.html" to """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Page</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <h1>Hello, offline world</h1>
    <button id="btn">Click me</button>
    <p id="out"></p>
    <script src="script.js"></script>
</body>
</html>
""",
            "style.css" to """body {
    font-family: sans-serif;
    margin: 24px;
}

button {
    padding: 8px 16px;
}
""",
            "script.js" to """document.getElementById('btn').addEventListener('click', () => {
    document.getElementById('out').textContent = 'Clicked at ' + new Date().toLocaleTimeString();
    console.log('button clicked');
});
"""
        )
    )

    private val js = ProjectTemplate(
        "js", "JavaScript (script)",
        mapOf(
            "main.js" to """// Runs in the Android WebView JS engine (browser environment, no Node APIs).
const nums = [1, 2, 3, 4, 5];
console.log('squares:', nums.map(n => n * n));
console.log({ hello: 'world' });
"""
        )
    )

    private val react = ProjectTemplate(
        "react", "React (JSX preview)",
        mapOf(
            "src/main.jsx" to """import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './styles.css';

createRoot(document.getElementById('root')).render(<App />);
""",
            "src/App.jsx" to """import React, { useState } from 'react';

export default function App() {
    const [count, setCount] = useState(0);
    return (
        <div className="app">
            <h1>React, offline</h1>
            <button onClick={() => setCount(count + 1)}>Clicked {count} times</button>
        </div>
    );
}
""",
            "src/styles.css" to """.app {
    font-family: sans-serif;
    margin: 24px;
}
"""
        )
    )

    private val python = ProjectTemplate(
        "python", "Python",
        mapOf(
            "main.py" to """def greet(name):
    return f"Hello, {name}!"


if __name__ == "__main__":
    print(greet("world"))
"""
        )
    )

    private val php = ProjectTemplate(
        "php", "PHP (CLI)",
        mapOf(
            "index.php" to """<?php
function greet(string ${'$'}name): string {
    return "Hello, ${'$'}name!";
}

echo greet("world") . PHP_EOL;
"""
        )
    )

    private val java = ProjectTemplate(
        "java", "Java",
        mapOf(
            "Main.java" to """public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, world!");
    }
}
"""
        )
    )

    private val c = ProjectTemplate(
        "c", "C",
        mapOf(
            "main.c" to """#include <stdio.h>

int main(void) {
    printf("Hello, world!\n");
    return 0;
}
"""
        )
    )

    private val cpp = ProjectTemplate(
        "cpp", "C++",
        mapOf(
            "main.cpp" to """#include <iostream>

int main() {
    std::cout << "Hello, world!" << std::endl;
    return 0;
}
"""
        )
    )

    val all: List<ProjectTemplate> = listOf(web, js, react, python, php, java, c, cpp)
}
