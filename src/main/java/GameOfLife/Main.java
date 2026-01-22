package GameOfLife;

import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        List<List<String>> starting = transposedGlider();
        Set<Cell> cells = Game.convertTableToCells(starting);
        Size size = new Size(10, 10);
        while (true) {
            List<List<String>> strings = Game.convertCellsToTable(cells, size);
            Utility.clearScreen();
            Game.printOutputTable(strings);
            Thread.sleep(500);
            cells = Game.tick(cells);
        }

    }

    private static List<List<String>> blinker() {
        List<List<String>> starting = List.of(

                List.of("", "X", ""), List.of("", "X", ""), List.of("", "X", ""));
        return starting;
    }

    private static List<List<String>> glider() {
        List<List<String>> starting = List.of(List.of("", "X", "", "", ""), List.of("", "", "X", "", ""), List.of("X", "X", "X", "", ""), List.of("", "", "", "", ""), List.of("", "", "", "", ""), List.of("", "", "", "", ""));
        return starting;
    }

    private static List<List<String>> transposedGlider() {
        List<List<String>> starting = List.of(List.of("", "", "", "", "", ""), List.of("", "", "X", "X", "X", ""), List.of("", "", "X", "", "", ""), List.of("", "", "", "X", "", ""), List.of("", "", "", "", "", ""), List.of("", "", "", "", "", ""));

        return starting;
    }


}
