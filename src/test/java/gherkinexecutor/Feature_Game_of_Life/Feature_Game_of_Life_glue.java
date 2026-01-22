package gherkinexecutor.Feature_Game_of_Life;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import GameOfLife.Cell;
import GameOfLife.Game;
import GameOfLife.Size;

class Feature_Game_of_Life_glue {
    final String DNCString = "?DNC?";
    Set<Cell> currentGeneration;
    Set<Cell> nextGeneration;

    static int minimumX = Integer.MAX_VALUE;
    static int minimumY = Integer.MAX_VALUE;
    static int maximumX = Integer.MIN_VALUE;
    static int maximumY = Integer.MIN_VALUE;

    Size size;
    Size sizeOfTable(List<List<String>> strings) {
         int ySize = strings.size();
         int xSize = strings.get(0).size();
         return new Size(xSize, ySize);
 }

    void setMinMax(Set<Cell> cells){
        for (Cell c : cells){
            if (c.x < minimumX)
                minimumX = c.x;
            if (c.y < minimumY)
                minimumY = c.y;
            if (c.x > maximumX)
                maximumX = c.x;
            if (c.y > maximumY)
                maximumY = c.y;
        }
    }

    static List<List<String>> adjustTable(List<List<String>> strings){
        List<List<String>> result = new ArrayList<>();
        int y = 0;
        for (List<String> list : strings) {
            int x = 0;
            List<String> row = new ArrayList<>();
            for (String s: list){
                if (s.toUpperCase().trim().equals("X"))
                    row.add("X");
                else
                    row.add(" ");
                x += 1;
            }
            result.add(row);
            y += 1;
        }
        return result;
    }

    static void printGherkinTable(List<List<String>> strings)
    {
        for (List<String> row : strings) {
            System.out.print("|");
            for (String s: row){
                System.out.print(s+"|");
            }
            System.out.println();
         }
    }
    void Given_generation_is(List<List<String>> values ) {
        System.out.println("---  " + "Given_generation_is");
        size = sizeOfTable(values);
        Set<Cell> cells = Game.convertTableToCells(values);
        currentGeneration = cells;
    }

    void When_tick(){
        System.out.println("---  " + "When_tick");
        nextGeneration = Game.tick(currentGeneration);
    }
    void When_output(){
        System.out.println("---  " + "When_output");
        nextGeneration = currentGeneration;
    }


    void Then_generation_is_now(List<List<String>> values ) {
        System.out.println("---  " + "Then_generation_is_now");
        List<List<String>> expected = adjustTable(values);
        List<List<String>> result = Game.convertCellsToTable(nextGeneration, size);
        assertEquals(expected, result);
        currentGeneration = nextGeneration;
     }



    }
