package GameOfLife;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Game {
    public Game() {

    }

    static public Set<Cell> tick(Set<Cell> currentGeneration) {
        Set<Cell> nextGeneration = new HashSet<>();
        for (Cell cell : currentGeneration) {
            Set<Cell> neighbors = findNeighbors(cell);
            int alive = countAlive(neighbors, currentGeneration);
            if (alive == 2 || alive == 3) {
                nextGeneration.add(cell);
            }
            for (Cell c : neighbors) {
                if (currentGeneration.contains(c))
                    continue;
                if (countAlive(findNeighbors(c), currentGeneration) == 3) {
                    nextGeneration.add(c);
                }
            }
        }
        return nextGeneration;
    }

    private static int countAlive(Set<Cell> neighbors, Set<Cell> currentGeneration) {
        int alive = 0;
        for (Cell cell : neighbors) {
            if (currentGeneration.contains(cell))
                alive += 1;
        }
        return alive;
    }

    static Set<Cell> findNeighbors(Cell cell) {
        Set<Cell> result = new HashSet<>();
        int x = cell.x;
        int y = cell.y;
        Set<Cell> offsets = Set.of(
                new Cell(-1, -1),
                new Cell(-1, 0),
                new Cell(-1, 1),
                new Cell(0, -1),
                new Cell(0, 1),
                new Cell(1, -1),
                new Cell(1, 0),
                new Cell(1, 1)
        );
        for (Cell o : offsets) {
            result.add(new Cell(cell.x + o.x, cell.y + o.y));
        }
        return result;
    }

    static void printOutputTable(List<List<String>> strings) {
        for (List<String> row : strings) {
            for (String s : row) {
                if (s.equals(" "))
                    System.out.print(".");
                else
                    System.out.print(s);
            }
            System.out.println();
        }
    }

    public static List<List<String>> convertCellsToTable(Set<Cell> cells, Size size) {

        List<List<String>> strings = new ArrayList<>();
        for (int y = 0; y < size.y; y++) {
            List<String> row = new ArrayList<>();
            for (int x = 0; x < size.x; x++) {
                row.add(" ");
            }
            strings.add(row);
        }
        for (Cell cell : cells) {
            int x = cell.x;
            int y = cell.y;
            if (x < 0 || x >= size.x || y < 0 || y >= size.y)
                continue;
            strings.get(y).set(x, "X");
        }
        return strings;
    }

    static void printTable(List<List<String>> strings) {
        for (List<String> row : strings) {
            System.out.println(row);
        }
    }

    public static Set<Cell> convertTableToCells(List<List<String>> strings) {
        int y = 0;
        Set<Cell> result = new HashSet<Cell>();
        for (List<String> list : strings) {
            int x = 0;
            for (String s : list) {
                if (s.toUpperCase().trim().equals("X")) {
                    result.add(new Cell(x, y));
                }
                x += 1;
            }
            y += 1;
        }
        return result;
    }
}
