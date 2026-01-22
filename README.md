# Game of Life



This is Conway's Game of Life using Gherkin scenarios as the test framework.    This is an example of visual specifications - the tables show the original and generated shapes.    The "X",s represent an alive cell.   

There is a main program which displays in a terminal window.    

You can add your own shape by adding it to the feature file (at the bottom), running Translate to convert it into Java, and then copying the generated code to the main program.    See blinker, glider, and transposedGlider for examples.    

The algorithim is in Game. It is less than 50 lines long, including helper methods for determining neighbors and alive counts. 

There are functions written for converting from lists of strings to sets of alive cells, and back again.   


