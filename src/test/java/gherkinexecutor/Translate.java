package gherkinexecutor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@SuppressWarnings({"CommentedOutCode","EnhancedSwitchMigration"})
public class Translate {
    private final Map<String, String> scenarios = new HashMap<>(); // used to check if duplicate scenario names
    private final Map<String, String> glueFunctions = new HashMap<>(); // used to make sure only one glue implementation
    private final Map<String, String> dataNames = new HashMap<>(); // used to check for duplicate data
    private final Map<String, String> dataNamesInternal = new HashMap<>(); // used to check for duplicate data
    private final Map<String, String> importNames = new HashMap<>(); // used to hold conversion functions for imports

    private final List<String> linesToAddForDataAndGlue = new ArrayList<>();

    private final List<String> linesToAddToEndOfGlue = new ArrayList<>();
    private final Map<String, String> defineNames = new HashMap<>();
    private final int stepCount = 0; // use to label duplicate scenarios
    //    private final String basePath = Configuration.testSubDirectory;
    private String glueClass = "";  // glue class name
    private String glueObject = "";  // glue object name

    private int stepNumberInScenario = 0;  // use to label variables in scenario
    private InputIterator dataIn = new InputIterator("", "", this );
    private boolean firstScenario = true; // If first scenario
    private boolean addBackground = false;  // Have seen Background
    private boolean addCleanup = false;  // have seen Cleanup

    private boolean inCleanup = false;  // Current scenario is Cleanup
    private boolean finalCleanup = false; // for the last part of scenario
    // Create the output files, save names for deletions
//    private final String testFilename = basePath + "Test" + ".tmp";
    private FileWriter testFile;

    // private FileWriter aTestFile;  - to not create tmp file
    private boolean featureActedOn = false; // Have found a feature step
    private String featureName = "";

    private String directoryName = "";

    private String featureDirectory = ""; // if feature file is in a directory

    private String featurePackagePath = "";
    String packagePath = "Not Set";

    private final List<String> classDataNames = new ArrayList<>();

    private final DataConstruct dataConstruct = new DataConstruct();

    private final TemplateConstruct templateConstruct = new TemplateConstruct();

    private final StepConstruct stepConstruct = new StepConstruct();

    private final ImportConstruct importConstruct = new ImportConstruct();
    private final DefineConstruct defineConstruct = new DefineConstruct();

    private final String filterExpression = Configuration.filterExpression;
    private boolean skipSteps = false;

    private int scenarioCount = 0; // Number of scenarios encountered
    private int backgroundCount = 0; // Number of backgrounds encountered
        // if > 1, duplicate backgrounds
    private int cleanupCount = 0; // Number of cleanups encountered
        // if > 1 duplicate cleanups

    private static final String TAG_INDICATOR = "@";
    private String tagLine = ""; // Contains last tag line

    private int tagLineNumber = 0; // Line number for last tage line

    public Translate() {
    }

    public void translateInTests(String name) {
        findFeatureDirectory(name);

        linesToAddForDataAndGlue.addAll(Configuration.linesToAddForDataAndGlue);
        dataIn = new InputIterator(name, featureDirectory, this );
        alterFeatureDirectory();
        if (dataIn.isEmpty())
            return;

        for (int pass = 1; pass <= 3; pass++) {
            dataIn.reset();
            boolean eof = false;
            while (!eof) {
                String line = dataIn.next();
                if (line.equals(InputIterator.EOF)) {
                    eof = true;
                    continue;
                }
                actOnLine(line, pass);
            }
        }
        endUp();
    }

    private void alterFeatureDirectory() {
        // Remove feature directory from the package and directory
        String searchFor = Configuration.treeDirectory;
        String alternateSearchFor = searchFor.replace("/", "\\");
        String directory = featureDirectory.replace(searchFor, "");
        directory = directory.replace(alternateSearchFor, "");
        featureDirectory = directory;
        featurePackagePath = featureDirectory.replace("\\", ".").replace("/", ".");
    }

    private void findFeatureDirectory(String name) {
        String directory = "";
        int indexForward = name.lastIndexOf('/');
        int indexBack = name.lastIndexOf('\\');
        int index = (Math.max(indexForward, indexBack));
        if (index >= 0)
            directory = name.substring(0, index + 1);
        featureDirectory = directory;
        featurePackagePath = featureDirectory.replace("\\", ".").replace("/", ".");
    }

    private void actOnLine(String line, int pass) {
        Pair<List<String>, List<String>> splitLine = splitLine(line,pass );
        List<String> words = splitLine.getFirst();
        List<String> comment = splitLine.getSecond();
        if (words.size() > 0) {
            String keyword = wordWithOutColon(words.get(0));
            // Note sure what the above is happening
            if (keyword.equals("*")) {
                keyword = "Star";
            }
            words.set(0, keyword);
            actOnKeyword(keyword, words, comment, pass);
        }
    }

    private Pair<List<String>, List<String>> splitLine(String line, int pass) {
        String[] allWords = line.split(" ");
        List<String> words = new ArrayList<>();
        List<String> comment = new ArrayList<>();
        if ((pass == 3 || pass == 2) && line.trim().startsWith(TAG_INDICATOR)){
            tagLine = line;
            tagLineNumber = dataIn.getLineNumber();
        }
        boolean inComment = false;
        for (String aWord : allWords) {
            String word = aWord.trim();
            if (word.isEmpty()) continue;
            if (word.endsWith(":")) {
                word = wordWithOutColon(word);
            }
            if (word.isEmpty()) continue;
            if (inComment) {
                comment.add(word);
                continue;
            }
            if (word.charAt(0) == '#') {
                inComment = true;
                word = wordWithOutHash(word);
                if (!word.isEmpty()) {
                    comment.add(word);
                }
                continue;
            }
            word = filterWord(word);
            words.add(word);
        }
        return new Pair<>(words, comment);
    }

    public static String filterWord(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[^0-9a-zA-Z_*]", "");
    }

    private static String wordWithOutColon(String word) {
        return word.replaceAll("^:+|:+$", "");
    }

    private static String wordWithOutHash(String word) {
        return word.replaceAll("^#+|#+$", "");
    }

    private void actOnKeyword(String keyword, List<String> words,
                              List<String> comment, int pass) {
        String fullName = makeFullName(words);
        trace("Act on keyword " + keyword + " " + fullName + " words " + words + " pass " + pass);

        if (keyword.equals("Star")) {
            switch (words.get(1)) {
                case "Data":
                    keyword = "Data";
                    words.remove(0);
                    break;
                case "Import":
                    keyword = "Import";
                    words.remove(0);
                    break;
                case "Define":
                    keyword = "Define";
                    words.remove(0);
                    break;
                case "Cleanup":
                    keyword = "Cleanup";
                    words.remove(0);
                    break;
                default:
                    break;
            }
        }
        switch (keyword) {
            case "Feature":
                if (pass != 2)
                    break;
                actOnFeature(fullName);
                if (TagFilterEvaluator.shouldNotExecute(comment, filterExpression)) {
                    dataIn.goToEnd();  // skip remainder of file
                    System.out.println(" Skip Entire Feature ");
                }
                break;
            case "Scenario":
                if (pass != 3)
                    break;
                if (TagFilterEvaluator.shouldNotExecute(comment, filterExpression)) {
                    skipSteps = true;
                    break;
                }
                skipSteps = false;
                actOnScenario(fullName);
                inCleanup = false;
                break;
            case "Background":
                addBackground = true;
                if (pass != 3)
                    break;
                skipSteps = false;
                actOnBackground(fullName);
                inCleanup = true;  // Don't want cleanup to be added
                break;
            case "Cleanup":
                addCleanup = true;
                if (pass != 3)
                    break;
                skipSteps = false;
                actOnCleanup(fullName);
                inCleanup = true;
                break;
            case "But":
            case "Given":
            case "When":
            case "Then":
            case "And":
            case "Star":
            case "Arrange":
            case "Act":
            case "Assert":
            case "Rule":
            case "Calculation":
            case "Trigger":
            case "Verify":
            case "Assemble":
            case "Activate":
            case "Preconditions":
            case "MainCourse":
            case "Exception":
            case "Postconditions":
                if (pass != 3)
                    break;
                if (skipSteps)
                    break;
                stepConstruct.actOnStep(fullName, comment);
                break;
            case "Data":
                if (pass != 2)
                    break;
                skipSteps = false;
                dataConstruct.actOnData(words);
                break;
            case "Import":
                if (pass != 1)
                    break;
                skipSteps = false;
                importConstruct.actOnImport(words);
                break;
            case "Define":
                if (pass != 1)
                    break;
                skipSteps = false;
                defineConstruct.actOnDefine(words);
                break;
            default:
                // line not used this pass or bad line
                break;
        }
    }

    private void checkForTagLine() {
        if (tagLine.isEmpty())
            return;

        if (tagLineNumber + 1 == dataIn.getLineNumber()) {

            testPrint(tagLine);
        }
        tagLine = "";
        tagLineNumber = 0;

    }

    void writeInputFeature(String filename) {
        String fullFilename = filename + "feature.txt";
        printFlow("Logging to " + fullFilename);
        try {
            FileWriter myLog = new FileWriter(fullFilename);
            myLog.write(dataIn.toString());
            myLog.close();
        } catch (IOException e) {
            System.err.println(e.getMessage() + " Cause " + e.getCause());
            System.err.println("**** Cannot write to " + fullFilename);
        }
    }

    private static String makeFullName(List<String> words) {
        String temp = String.join("_", words);
        Pattern p = Pattern.compile("[^A-Za-z0-9_]");
        Matcher m = p.matcher(temp);
        return m.replaceAll("_");
    }

    private void actOnFeature(String fullName) {
        if (actOnFeatureFirstHalf(fullName)) return;
        testPrint("package " + packagePath + ";");
        switch (Configuration.testFramework) {
            //noinspection DataFlowIssue
            case "JUnit4":
                testPrint("import org.junit.*;");
                break;
            //noinspection DataFlowIssue
            case "TestNG":
                testPrint("import org.testng.annotations.*;");
                break;
            default:
                testPrint("import org.junit.jupiter.api.*;");
        }
        testPrint("import java.util.List;");
        if (Configuration.logIt) {
            testPrint("import java.io.FileWriter;");
            testPrint("import java.io.IOException;");
        }
        testPrint("@SuppressWarnings({\"NewClassNamingConvention\"})");
        checkForTagLine();
        testPrint("class " + fullName + "{");
        testPrint(logIt());
        testPrint("");

        templateConstruct.beginTemplate();
    }

    private boolean actOnFeatureFirstHalf(String fullName) {
        if (featureActedOn) {
            warning("Feature keyword duplicated - it is ignored " + fullName);
            return true;
        }
        featureName = fullName;
        featureActedOn = true;
        packagePath = Configuration.addToPackageName + Configuration.packageName + "." + featurePackagePath + featureName;
        printFlow("Package is " + packagePath);
        String testPathname = Configuration.testSubDirectory + featureDirectory + featureName + "/" +
                featureName + ".java";
        printFlow(" Writing " + testPathname);
        String templateFilename = Configuration.testSubDirectory + featureDirectory + featureName + "/" +
                featureName + "_glue.tmpl";
//        cleanFiles();
        directoryName = Configuration.testSubDirectory + featureDirectory + featureName;
        printFlow("Directory " + directoryName + " ");
        try {
            boolean result = new File(directoryName).mkdirs();
            if (!result)
                trace("Possible error in creating directory " + directoryName);
            testFile = new FileWriter(testPathname, false);
            templateConstruct.glueTemplateFile = new FileWriter(templateFilename, false);
        } catch (IOException e) {
            error("IO Exception in setting up the files");
            error(" Writing " + testPathname);

        }
        glueClass = fullName + "_glue";
        glueObject = makeName(fullName) + "_glue_object";
        writeInputFeature(Configuration.testSubDirectory + featureDirectory + featureName + "/");
        return false;
    }

    private String makeBuildName(String s) {
        // Upper case
        String temp = makeName(s);
        temp = Character.toUpperCase(temp.charAt(0)) + temp.substring(1);
        return "set" + temp;
    }

    private String makeName(String input) {
        if (input.isEmpty()) return "NAME_IS_EMPTY";
        String temp = input.replaceAll(" ", "_");
        temp = filterWord(temp);
        return Character.toLowerCase(temp.charAt(0)) + temp.substring(1);
    }

//    Is needed for kotlin to reverse initialization of variables
//    private void cleanFiles()  {
//        try {
//            testFile.close();
//            dataDefinitionFile.close();
//            glueTemplateFile.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//    }


    private void actOnScenario(String fullName) {
        scenarioCount++;
        String fullNameToUse = fullName;
        if (scenarios.containsKey(fullName)) {
            fullNameToUse += stepCount;
            error("Scenario name duplicated renamed " + fullNameToUse);
        } else {
            scenarios.put(fullNameToUse, "");
        }
        stepNumberInScenario = 0;
        // To make sure cleanup is called for final scenario
        finalCleanup = addCleanup;
        if (firstScenario) {
            firstScenario = false;
        } else { // Finishing up previous scenario
            if (addCleanup && !inCleanup)
                testPrint("        test_Cleanup(" + glueObject + "); // from previous");

            testPrint("        }"); // end previous scenario
        }

            checkForTagLine();
            testPrint("    @Test");
            testPrint("    void test_" + fullNameToUse + "(){");
            testPrint("         " + glueClass + " " + glueObject + " = new " + glueClass + "();");

        if (Configuration.logIt) {
            testPrint("        log(" + "\"" + fullNameToUse + "\"" + ");");
        }
        if (addBackground) {
            testPrint("        test_Background(" + glueObject + ");");
        }
    }

    private void actOnBackground(String fullName) {

        backgroundCount++;
        String fullNameToUse = fullName;
        finalCleanup = false;
        if (backgroundCount > 1){
            error("More than one Background statement");
            fullNameToUse += String.valueOf(backgroundCount);
        }

        stepNumberInScenario = 0;

        if (firstScenario) {
            firstScenario = false;
        } else {
            testPrint("        }"); // end previous scenario
        }
      testPrint("    void test_" + fullNameToUse + "(" + glueClass + " " + glueObject + "){");
      if (Configuration.logIt) {
            testPrint("        log(" + "\"" + fullNameToUse + "\"" + ");");
        }
    }
    private void actOnCleanup(String fullName) {

        cleanupCount++;
        finalCleanup = false;
        String fullNameToUse = fullName;
        if (cleanupCount > 1){
            error("More than one cleanup statement");
            fullNameToUse += String.valueOf(cleanupCount);
        }

        stepNumberInScenario = 0;

        if (firstScenario) {
            firstScenario = false;
        } else {
            testPrint("        }"); // end previous scenario
        }
        testPrint("    void test_" + fullNameToUse + "(" + glueClass + " " + glueObject + "){");
        if (Configuration.logIt) {
            testPrint("        log(" + "\"" + fullNameToUse + "\"" + ");");
        }
    }

    private String logIt() {
        if (Configuration.logIt) {
            String filename = directoryName + "/log.txt";
            return "void log(String value) {" + System.lineSeparator() +
                    "    try {" + System.lineSeparator() +
                    "        FileWriter myLog = new FileWriter(" +
                    "\"" + filename + "\"" + ", true);" + System.lineSeparator() +
                    "        myLog.write(value + \"\\n\");" + System.lineSeparator() +
                    "        myLog.close();" + System.lineSeparator() +
                    "    } catch (IOException e) {" + System.lineSeparator() +
                    "    System.err.println(\"*** Cannot write to log \");" + System.lineSeparator() +
                    "    }" + System.lineSeparator() +
                    "    }" + System.lineSeparator();
        } else
            return "";
    }

    private void trace(String value) {
        if (Configuration.traceOn) {
            System.out.println(value);
        }
    }

    boolean errorOccurred = false;

    private void error(String value) {
        System.err.println("[GherkinExecutor] " + "~ line " +
                this.dataIn.getLineNumber() + " in " + "feature.txt " +
                value + " ");
        errorOccurred = true;
    }

    private void warning(String value) {
        System.err.println("[GherkinExecutor] " + "Warning " +
                "~ line " + this.dataIn.getLineNumber() + " in "
                + "feature.txt " + value);
    }

    private static void printFlow(String value) {
        System.out.println(value);
    }

    private void testPrint(String line) {
        try {
            testFile.write(line);
            testFile.write("\n");
        } catch (IOException e) {
            error("IO ERROR ");
        }
    }


    private List<String> parseLine(String line) {
        String lineShort = line.trim();
        if (lineShort.charAt(0) == '|') {
            lineShort = lineShort.substring(1);
        } else {
            error("table not begin with | " + line);
            return List.of("ERROR IN TABLE LINE " + line);
        }
        int last = lineShort.length() - 1;
        if (last < 0) {
            error("table format error " + line);
            return List.of("ERROR IN TABLE LINE " + line);
        }
        if (lineShort.charAt(last) == '|') {
            lineShort = lineShort.substring(0, last);
        } else {
            error("table not end with | " + line);
        }
        String[] elements = lineShort.split("\\|");
        List<String> elementsTrimmed = new ArrayList<>();
        for (String element : elements) {
            String current = element.trim();
            current = current.replace(Configuration.spaceCharacters, ' ');
            current = replaceDefines(current);
            elementsTrimmed.add(current);
        }
        return elementsTrimmed;
    }

    private Pair<String, List<String>> lookForFollow() {
        String line = dataIn.peek();
        List<String> empty = new ArrayList<>();
        while (!line.isEmpty() && line.charAt(0) == '#') {
            dataIn.next();
            line = dataIn.peek();
        }
        line = line.trim();
        if (line.isEmpty()) return new Pair<>("NOTHING", empty);
        if (line.charAt(0) == '|') {
            List<String> retValue = readTable();
            trace("Table is " + retValue);
            return new Pair<>("TABLE", retValue);
        }
        if (line.equals("\"\"\"")) {
            List<String> retValue = readString();
            trace("Multi Line String is " + retValue);
            return new Pair<>("STRING", retValue);
        }
        return new Pair<>("NOTHING", empty);
    }

    private List<String> readTable() {
        List<String> retValue = new ArrayList<>();
        String line = dataIn.peek().trim();
        line = line.split("#")[0].trim();
        while (!line.isEmpty() && (line.charAt(0) == '|' || line.charAt(0) == '#')) {
            line = dataIn.next().trim();
            line = line.split("#")[0].trim();
            if (line.charAt(0) == '|' && line.endsWith("|")) {
                retValue.add(line);
            } else {
                error("Invalid line in table " + line);
            }
            line = dataIn.peek().trim();
        }
        return retValue;
    }

    private List<String> readString() {
        List<String> retValue = new ArrayList<>();
        String firstLine = dataIn.peek();
        int countIndent = countIndent(firstLine);
        dataIn.next();
        String line = dataIn.next();
        while (!line.trim().equals("\"\"\"")) {
            retValue.add(line.substring(countIndent));
            line = dataIn.next();
        }
        return retValue;
    }

    private String replaceDefines(String in) {
        boolean didReplacement = true;
        String changeString = in;
        while (didReplacement) {
            didReplacement = false;
            for (String name : defineNames.keySet()) {
                if (changeString.contains(name)) {
                    didReplacement = true;
                    String replacement = defineNames.get(name);
                    changeString = changeString.replace(name, replacement);
                }
            }
        }
        return changeString;
    }

    private int countIndent(String firstLine) {
        String line = firstLine.trim();
        return firstLine.length() - line.length();
    }


    public static List<String> findFeatureFiles(String directory) {
        List<String> featureFiles = new ArrayList<>();
        collectFeatureFiles(new File(directory), featureFiles);
        return featureFiles;
    }
    public static void readFilterList()
    {
        String filepath = Configuration.featureSubDirectory + "filter.txt";
        printFlow("Path is " + filepath);
        List<String> raw;
        try
        {
            raw = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(filepath));
        }
        catch (Exception e)
        {
//            System.out.println("Error: Unable to read " + e + filepath);
            return;
        }
        String [] arguments = {""};
        raw.toArray(arguments);
        Configuration.filterExpression = arguments[0];
        System.out.println("Filter is " + arguments[0]);
    }

    public static void readOptionList()
    {
        String filepath = Configuration.featureSubDirectory + "options.txt";
        printFlow("Path is " + filepath);
        List<String> raw;
        try
        {
            raw = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(filepath));
        }
        catch (Exception e)
        {
//            System.out.println("Error: Unable to read " + e + filepath);
            return;
        }
        String [] arguments = {""};
        raw.toArray(arguments);
        processArguments(arguments);
    }

    public static void readFeatureList() {
        String filepath = Configuration.featureSubDirectory + "features.txt";
        printFlow("Path is " + filepath);
        List<String> raw;
        try {
            raw = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(filepath));
        } catch (Exception e) {
//            System.out.println(" Unable to read " + filepath);
            return;
        }
        Configuration.featureFiles.addAll(raw);
    }


    private static void collectFeatureFiles(File dir, List<String> featureFiles) {
        String remove = Configuration.featureSubDirectory;
        remove = remove.replace("/", "\\");
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        collectFeatureFiles(file, featureFiles);
                    } else if (file.getName().endsWith(".featurex")) {
                        String path = file.getPath();
                        path = path.replace(remove, "");
                        featureFiles.add(path);
                    }
                }
            }
        }
    }

    @SuppressWarnings("ConstantValue")
    public static void main(String[] args) {
        printFlow("Gherkin Executor");
        Configuration.currentDirectory = System.getProperty("user.dir");
        processArguments(args);
        if (Configuration.searchTree && !Configuration.startingFeatureDirectory.isEmpty()) {
            List<String> filesInTree = findFeatureFiles(Configuration.startingFeatureDirectory);
            printFlow("Adding directory tree files");
            filesInTree.forEach(System.out::println);
            Configuration.featureFiles.addAll(filesInTree);
        }
        readOptionList();
        readFilterList();
        readFeatureList();
        for (String name : Configuration.featureFiles) {
            Translate translate = new Translate();
            printFlow("Translating " + name);
            translate.translateInTests(name);
        }
    }

    private static void processArguments(String[] args) {
        printFlow("Optional arguments are logIt inTest searchTree traceOn");
        boolean filterNext = false;
        for (String arg : args) {
            printFlow("Program argument: " + arg);
            switch (arg) {
                case "logIt":
                    Configuration.logIt = true;
                    printFlow("logIt on");
                    break;
                case "inTest":
                    Configuration.inTest = true;
                    printFlow("inTest on");
                    break;
                case "traceOn":
                    Configuration.traceOn = true;
                    printFlow("traceOn true");
                    break;
                case "searchTree":
                    Configuration.searchTree = true;
                    printFlow("searchTree on");
                    break;
                case "--filter":
                    filterNext = true;
                    break;
                default:
                    if (filterNext){
                        filterNext = false;
                        Configuration.filterExpression = arg;
                        break;
                    }
                    Configuration.featureFiles.add(arg);
            }
        }
    }


    public String quoteIt(String defaultVal) {
        return "\"" + defaultVal + "\"";
    }

    public String fromImportData(String dataType, String value) {
        if (importNames.containsKey(dataType)) {
            String conversionMethod = importNames.get(dataType);
            conversionMethod = conversionMethod.replace("$", value);
            return conversionMethod;
        } else
            return "";
    }

    @SuppressWarnings("SameParameterValue")
    public String makeValueFromString(DataConstruct.DataValues variable, boolean makeNameValue) {
        String value;
        if (makeNameValue)
            value = makeName(variable.name);
        else
            value = quoteIt(variable.defaultVal);
        switch (variable.dataType) {
            case "String":
                return value;
            case "int":
                return "Integer.parseInt(" + value + ")";
            case "double":
                return "Double.parseDouble(" + value + ")";
            case "byte":
                return "Byte.parseByte(" + value + ")";
            case "short":
                return "Short.parseShort(" + value + ")";
            case "long":
                return "Long.parseLong(" + value + ")";
            case "float":
                return "Float.parseFloat(" + value + ")";
            case "boolean":
            case "Boolean":
                return "Boolean.parseBoolean(" + value + ")";
            case "char":
                return "( " + value + ".length() > 0 ?"
                        + value + ".charAt(0) : ' ')";
            case "Byte":
                return "Byte.valueOf(" + value + ")";
            case "Short":
                return "Short.valueOf(" + value + ")";
            case "Integer":
                return "Integer.valueOf(" + value + ")";
            case "Long":
                return "Long.valueOf(" + value + ")";
            case "Float":
                return "Float.valueOf(" + value + ")";
            case "Double":
                return "Double.valueOf(" + value + ")";
            case "Character":
                return "Character.valueOf( " + value + ".length() > 0 ?"
                        + value + ".charAt(0) : ' ')";
            default:
                String result = fromImportData(variable.dataType, value);
                if (!result.isEmpty())
                    return result;
                return "new " + variable.dataType + "(" + value + ")";  // Data type not found;

        }
    }

    private void endUp() {
        if (finalCleanup) {
            testPrint("        test_Cleanup(" + glueObject + "); // at the end");
        }
        if (scenarioCount == 0 ) {// Then no scenarios
            System.out.println("No scenarios");
         }
        else
            testPrint("        }");   // End last scenario
        testPrint("    }"); // End the class
        testPrint("");
        try {
            testFile.close();
        } catch (IOException e) {
            error("Error in closing ");
        }

        templateConstruct.endTemplate();
        if (errorOccurred) {
            System.err.println("*** Error in translation, scan the output");
            System.exit(-1);
        }
        dataConstruct.endOneDataFile();
    }


static class InputIterator {

    private final Translate outer;


    private final List<String> linesIn = new ArrayList<>();
    @SuppressWarnings("UnusedAssignment")
    private int index = 0;

    public int getLineNumber() {
        return index;
    }

    @Override
    public String toString() {
        StringBuilder temp = new StringBuilder();
        for (String line : linesIn) {
            temp.append(line);
            temp.append("\n");
        }
        return temp.toString();
    }

    public static final String EOF = "EOF";

    final private String featureDirectory;

    public InputIterator(String name, String featureDirectory, Translate outer) {
        index = 0;
        this.featureDirectory = featureDirectory;
        this.outer = outer;
        if (!name.isEmpty()) {
            readFile(name, 0);
        }
    }

    private void readFile(String fileName, int includeCount) {
//            printFlow("Reading file " + fileName);
        includeCount++;
        if (includeCount > 20) {
            outer.error("Too many levels of include");
            return;
        }
        try {
            String filepath = Configuration.featureSubDirectory + fileName;
            printFlow("Path is " + filepath);
            List<String> raw;
            try {
                raw = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(filepath));
            } catch (Exception e) {
                outer.error(" Unable to read " + filepath);
                return;
            }
            for (String line : raw) {
                if (line.startsWith("Include")) {
                    String[] parts = line.split("\"");
                    outer.trace("Parts are " + String.join(", ", parts));
                    boolean localFile = true;
                    if (parts.length < 2) {
                        parts = line.split("'");
                        localFile = false;
                        if (parts.length < 2) {
                            outer.error("Error filename not surrounded by quotes: " + line);
                            continue;
                        }
                    }
                    if (parts[1].isEmpty()) {
                        outer.error("Error zero length filename " + line);
                        continue;
                    }
                    String includedFileName = parts[1].trim();
                    if (localFile)
                        includedFileName = featureDirectory + includedFileName;
                    outer.trace("Including " + includedFileName);
                    if (includedFileName.endsWith(".csv")) {
                        includeCSVFile(includedFileName);
                    } else {
                        readFile(includedFileName, includeCount);
                    }
                } else {
                    if (!line.isEmpty() && line.charAt(0) != '#') {
                        linesIn.add(line.trim());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //noinspection UnusedAssignment
        includeCount--;
    }

    private void includeCSVFile(String includedFileName) {
        try {
            List<String> raw = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(Configuration.featureSubDirectory + includedFileName));
            for (String line : raw) {
                if (line.isEmpty()) continue;
                String contents = convertCSVtoTable(line);
                linesIn.add(contents.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String convertCSVtoTable(String csvData) {
        String[] lines = csvData.split("\n");
        List<List<String>> data = new ArrayList<>();
        for (String line : lines) {
            data.add(parseCsvLine(line));
        }
        List<String> formattedData = new ArrayList<>();
        for (List<String> row : data) {
            formattedData.add("|" + String.join("|", row) + "|");
        }
        return String.join("\n", formattedData);
    }

    public List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        int length = line.length();
        for (int i = 0; i < length; i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < length && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    public String peek() {
        if (index < linesIn.size()) {
            return linesIn.get(index);
        } else {
            return EOF;
        }
    }

    public String next() {
        if (index < linesIn.size()) {
            return linesIn.get(index++);
        } else {
            return EOF;
        }
    }
    public void goToEnd(){
        index = linesIn.size();
    }




    public boolean isEmpty() {
        return linesIn.isEmpty();
    }

    public void reset() {
        index = 0;
    }
}


static class Pair<K, V> {
    private final K key;
    private final V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getFirst() {
        return key;
    }

    public V getSecond() {
        return value;
    }

    @Override
    public String toString() {
        return "Pair{" + "key=" + key + ", value=" + value + '}';
    }
}


class StepConstruct {
    private void actOnStep(String fullName, List<String> comment) {
        stepNumberInScenario += 1;
        Pair<String, List<String>> follow = lookForFollow();
        String followType = follow.getFirst();
        List<String> table = follow.getSecond();
        testPrint("");
        switch (followType) {
            case "TABLE":
                createTheTable(comment, table, fullName);
                break;
            case "NOTHING":
                noParameter(fullName);
                break;
            case "STRING":
                createTheStringCode(comment, table, fullName);
                break;
            default:
                error("Internal Error - Follow type " + followType);
        }
    }


    private void createTheStringCode(List<String> comment, List<String> table, String fullName) {
        String option = "String";
        if (comment.size() > 0 && !comment.get(0).isEmpty()) option = comment.get(0);
        if (option.equals("ListOfString")) stringToList(table, fullName);
        else stringToString(table, fullName);
    }

    private void stringToList(List<String> table, String fullName) {
        String s = Integer.toString(stepNumberInScenario);
        String dataType = "List<String>";
        String dataTypeInitializer = "List.of";
        testPrint("        List<String> stringList" + s + " = " + dataTypeInitializer + "(");
        String comma = "";
        for (String line : table) {
            testPrint("            " + comma + "\"" + line + "\"");
            comma = ",";
        }
        testPrint("            );");
        testPrint("        " + glueObject + "." + fullName + "(stringList" + s + ");");
        templateConstruct.makeFunctionTemplateIsList(dataType, fullName, "String");
    }

    private void stringToString(List<String> table, String fullName) {
        String s = Integer.toString(stepNumberInScenario);
        testPrint("        String string" + s + " =");
        testPrint("            \"\"\"");
        for (String line : table) {
            testPrint("            " + line);
        }
        testPrint("            \"\"\".stripIndent();");
        testPrint("        " + glueObject + "." + fullName + "(string" + s + ");");
        templateConstruct.makeFunctionTemplate("String", fullName);
    }

    private void tableToListOfListOfObject(List<String> table, String fullName, String className) {
        String s = Integer.toString(stepNumberInScenario);
        String dataType = "List<List<String>>";
        String dataTypeInitializer = "List.of";

        testPrint("        List<List<String>> stringListList" + s + " = " + dataTypeInitializer + "(");
        String comma = "";
        for (String line : table) {
            convertBarLineToList(line, comma);
            comma = ",";
        }
        testPrint("            );");
        testPrint("        " + glueObject + "." + fullName + "(stringListList" + s + ");");
        templateConstruct.makeFunctionTemplateObject(dataType, fullName, "" + className);
        createConvertTableToListOfListOfObjectMethod(className);
    }

    private void tableToListOfList(List<String> table, String fullName) {
        String s = Integer.toString(stepNumberInScenario);
        String dataType = "List<List<String>>";
        String dataTypeInitializer = "List.of";

        testPrint("        List<List<String>> stringListList" + s + " = " + dataTypeInitializer + "(");
        String comma = "";
        for (String line : table) {
            convertBarLineToList(line, comma);
            comma = ",";
        }
        testPrint("            );");
        testPrint("        " + glueObject + "." + fullName + "(stringListList" + s + ");");
        templateConstruct.makeFunctionTemplateIsList(dataType, fullName, "List<String>");
    }


    private void createTheTable(List<String> comment, List<String> table, String fullName) {
        String option = "ListOfList";
        String className;
        if (comment.size() > 0 && !comment.get(0).isEmpty()) option = comment.get(0);
        switch (option) {
            case "ListOfList":
                tableToListOfList(table, fullName);
                break;
            case "ListOfListOfObject":
                if (comment.size() < 2) {
                    error("No class name specified");
                    return;
                }
                className = comment.get(1);
                tableToListOfListOfObject(table, fullName, className);
                break;
            case "String":
            case "string":
                tableToString(table, fullName);
                break;
            case "ListOfObject":
                if (comment.size() < 2) {
                    error("No class name specified");
                    return;
                }
                className = comment.get(1);
                boolean transpose = false;
                boolean compare = false;
                if (comment.size() > 2) {
                    String action = comment.get(2);
                    if (action.equals("compare") || action.equals("Compare"))
                        compare = true;
                    else if (!(action.equals("transpose") || action.equals("Transpose"))) {
                        error("Action not recognized " + action);
                    } else {
                        transpose = true;
                    }
                }
                tableToListOfObject(table, fullName, className, transpose, compare);
                break;
            default:
                error("Option not found, default to ListOfList " + option);
                tableToListOfList(table, fullName);
                break;
        }
    }


    private void createConvertTableToListOfListOfObjectMethod(String toClass) {
        DataConstruct.DataValues variable = new DataConstruct.DataValues("s", "s", toClass);
        String convert = makeValueFromString(variable, true);

        String template =
                """
                                public static List<List<CLASS>> convertList(List<List<String>> stringList) {
                                    List<List<CLASS>> classList = new ArrayList<>();
                                    for (List<String> innerList : stringList) {
                                        List<CLASS> innerClassList = new ArrayList<>();
                                        for (String s : innerList) {
                                            innerClassList.add(CONVERT);
                                        }
                                        classList.add(innerClassList);
                                    }
                                return classList;
                                }
                        """.stripIndent();
        template = template.replace("CLASS", toClass);
        template = template.replace("CONVERT", convert);
        linesToAddToEndOfGlue.add(template);
    }


    private void tableToString(List<String> table, String fullName) {
        String s = Integer.toString(stepNumberInScenario);
        testPrint("        String table" + s + " =");
        testPrint("            \"\"\"");
        for (String line : table) {
            testPrint("            " + line);
        }
        testPrint("            \"\"\".stripIndent();");
        testPrint("        " + glueObject + "." + fullName + "(table" + s + ");");
        // test_print("");
        templateConstruct.makeFunctionTemplate("String", fullName);
    }

    private void convertBarLineToList(String lineIn, String commaIn) {
        String line = lineIn.split("#")[0].trim();
        testPrint("           " + commaIn + "List.of(");
        List<String> elements = parseLine(line);
        String comma = "";
        for (String element : elements) {
            testPrint("            " + comma + "\"" + element + "\"");
            comma = ",";
        }
        testPrint("            )");
    }


    private void tableToListOfObject(List<String> table, String fullName, String className, boolean transpose, boolean compare) {
        trace("TableToListOfObject classNames " + className);
        String s = Integer.toString(stepNumberInScenario);
        String dataType = "List<" + className + ">";
        String dataTypeInitializer = "List.of(";
        //(List.of(
        testPrint("        List<" + className + "> objectList" + s + " = " + dataTypeInitializer + "");
        boolean inHeaderLine = true;
        List<List<String>> dataList = convertToListList(table, transpose);
        List<String> headers = new ArrayList<>();
        String comma = "";
        for (List<String> row : dataList) {
            if (inHeaderLine) {
                headers = row;
                for (String dataName : row) {
                    if (!findDataClassName(className, makeName(dataName))) {
                        error("Data name " + dataName + " not in Data for " + className);
                    }
                }

                inHeaderLine = false;
                continue;
            }

            convertBarLineToParameter(headers, row, className, comma, compare);
            comma = ",";
        }
        testPrint("            );");
        testPrint("        " + glueObject + "." + fullName + "(objectList" + s + ");");

        templateConstruct.makeFunctionTemplateIsList(dataType, fullName, className);
    }

    private List<List<String>> convertToListList(List<String> table, boolean transpose) {
        List<List<String>> temporary = new ArrayList<>();
        for (String line : table) {
            temporary.add(parseLine(line));
        }
        List<List<String>> result = temporary;
        if (transpose) {
            result = transpose(temporary);
        }
        return result;
    }

    private boolean findDataClassName(String className, String dataName) {
        String compare = className + "#" + dataName;
        for (String value : classDataNames) {
            if (value.equals(compare))
                return true;
        }
        return false;
    }

    private void convertBarLineToParameter(List<String> headers, List<String> values, String className, String comma, boolean compare) {
        trace("Headers " + headers);
        int size = headers.size();
        if (headers.size() > values.size()) {
            size = values.size();
            error("not sufficient values, using what is there" + values);
        }
        testPrint("            " + comma + " new " + className + ".Builder()");
        if (compare)
            testPrint("             .setCompare()");
        for (int i = 0; i < size; i++) {
            String value = "\"" + values.get(i).replace(Configuration.spaceCharacters, ' ') + "\"";
            testPrint("                ." + makeBuildName(headers.get(i)) + "(" + value + ")");
        }
        testPrint("                .build()");
//            testPrint("                ");
    }


    private void noParameter(String fullName) {
        testPrint("        " + glueObject + "." + fullName + "();");
        templateConstruct.makeFunctionTemplateNothing("", fullName);
    }


    @SuppressWarnings("ForLoopReplaceableByForEach")
    public List<List<String>> transpose(List<List<String>> matrix) {
        List<List<String>> transposed = new ArrayList<>();
        for (int i = 0; i < matrix.get(0).size(); i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < matrix.size(); j++) {
                row.add(matrix.get(j).get(i));
            }
            transposed.add(row);
        }
        return transposed;
    }


}

class TemplateConstruct {
    //        private final String glueTemplateFilename = basePath + "Glue" + ".tmp";
    private FileWriter glueTemplateFile;

    private void templatePrint(String line) {
        try {
            glueTemplateFile.write(line);
            glueTemplateFile.write("\n");
        } catch (IOException e) {
            error("IO ERROR");
        }
    }

    private void makeFunctionTemplateObject(String dataType, String fullName, String listElement) {
        if (checkForExistingTemplate(dataType, fullName)) return; // already have a prototype
        glueFunctions.put(fullName, dataType);
        templatePrint("    void " + fullName + "(" + dataType + " values ) {");

        templatePrint("    List<List<" + listElement + ">> is = convertList(values);");
        templatePrint("    System.out.println(is);");

        if (Configuration.logIt) {
            templatePrint("        log(\"---  \" + " + "\"" + fullName + "\"" + ");");
        }
        if (!Configuration.inTest)
            templatePrint("        fail(\"Must implement\");");
        templatePrint("    }");
        templatePrint("");
    }

    private boolean checkForExistingTemplate(String dataType, String fullName) {
        if (glueFunctions.containsKey(fullName)) {
            String currentDataType = glueFunctions.get(fullName);
            if (!currentDataType.equals(dataType)) {
                error("function " + fullName + " datatype " + currentDataType + " not equals " + dataType);
                return true;
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("SameParameterValue")
    private void makeFunctionTemplateNothing(String dataType, String fullName) {
        if (checkForExistingTemplate(dataType, fullName)) return; // already have a prototype
        glueFunctions.put(fullName, dataType);
        templatePrint("    void " + fullName + "(){");
        templatePrint("        System.out.println(\"---  \" + " + "\"" + fullName + "\"" + ");");
        if (Configuration.logIt)
            templatePrint("        log(\"---  \" + " + "\"" + fullName + "\"" + ");");
        if (!Configuration.inTest)
            templatePrint("        fail(\"Must implement\");");
        templatePrint("    }");
        templatePrint("");
    }

    private void makeFunctionTemplateIsList(String dataType, String fullName, String listElement) {
        if (checkForExistingTemplate(dataType, fullName)) return; // already have a prototype
        glueFunctions.put(fullName, dataType);
        templatePrint("    void " + fullName + "(" + dataType + " values ) {");
        templatePrint("        System.out.println(\"---  \" + " + "\"" + fullName + "\"" + ");");
        if (Configuration.logIt) {
            templatePrint("        log(\"---  \" + " + "\"" + fullName + "\"" + ");");
            templatePrint("        log(values.toString());");
        }
        String name = listElement + "Internal";
        templatePrint("        for (" + listElement + " value : values){");
        templatePrint("             System.out.println(value);");
        templatePrint("             // Add calls to production code and asserts");
        if (!dataType.equals("List<List<String>>")
                && !listElement.equals("String")
                && (dataNamesInternal.containsKey(name))) {
            templatePrint("              " + name + " i = value.to" + name + "();");
        }
        templatePrint("              }");

        if (!Configuration.inTest)
            templatePrint("        fail(\"Must implement\");");
        templatePrint("    }");
        templatePrint("");
    }

    @SuppressWarnings("SameParameterValue")
    private void makeFunctionTemplate(String dataType, String fullName) {
        if (checkForExistingTemplate(dataType, fullName)) return; // already have a prototype
        glueFunctions.put(fullName, dataType);
        templatePrint("    void " + fullName + "(" + dataType + " value ) {");
        templatePrint("        System.out.println(\"---  \" + " + "\"" + fullName + "\"" + ");");
        if (Configuration.logIt) {
            templatePrint("        log(\"---  \" + " + "\"" + fullName + "\"" + ");");
            templatePrint("        log(value.toString());");
        }
        templatePrint("        System.out.println(value);");
        if (!Configuration.inTest)
            templatePrint("        fail(\"Must implement\");");
        templatePrint("    }");
        templatePrint("");
    }

    private void beginTemplate() {
        templatePrint("package " + packagePath + ";");
        for (String line : linesToAddForDataAndGlue) {
            templatePrint(line);
        }
        switch (Configuration.testFramework) {
            //noinspection DataFlowIssue
            case "JUnit4":
                templatePrint("import static org.junit.Assert.*;");
                break;
            //noinspection DataFlowIssue
            case "TestNG":
                templatePrint("import static org.testng.Assert.*;");
                break;
            default:
                templatePrint("import static org.junit.jupiter.api.Assertions.*;");
        }
        templatePrint("import java.util.List;");
        if (Configuration.logIt) {
            templatePrint("import java.io.FileWriter;");
            templatePrint("import java.io.IOException;");
        }
        templatePrint("");
        templatePrint("class " + glueClass + " {");
        templatePrint("    final String DNCString = "
                + "\"" + Configuration.doNotCompare + "\";");
        templatePrint(logIt());

        templatePrint("");
    }

    private void endTemplate() {
        for (String line : linesToAddToEndOfGlue) {
            templatePrint(line);
        }
        templatePrint("    }");   // End the class
        try {
            testFile.close();
            glueTemplateFile.close();
        } catch (IOException e) {
            error("Error in closing ");
        }
    }

}

class DataConstruct {
    //        private final String dataDefinitionFilename = basePath + "DataDefinition" + ".tmp";
    private FileWriter dataDefinitionFile;
    final String throwString = ""; // needed if you want to catch errors in conversion methods

    @SuppressWarnings("unused")
    public static class DataValues {
        public final String name;
        public final String defaultVal;
        public final String dataType;
        public final String notes;

        public DataValues(String name, String defaultVal, String dataType, String notes) {
            this.name = name;
            this.defaultVal = defaultVal;
            this.dataType = dataType;
            this.notes = notes;
        }

        public DataValues(String name, String defaultVal, String dataType) {
            this(name, defaultVal, dataType, "");
        }

        public DataValues(String name, String defaultVal) {
            this(name, defaultVal, "String", "");
        }
    }


    private void actOnData(List<String> words) {
        String internalClassName;
        if (words.size() < 2) {
            error("Need to specify data class name");
        }
        String className = words.get(1);
        boolean providedOtherClassName;
        if (words.size() > 2) {
            internalClassName = words.get(2);
            providedOtherClassName = true;
        } else {
            providedOtherClassName = false;
            internalClassName = className + "Internal";
        }
        Pair<String, List<String>> follow = lookForFollow();
        String followType = follow.getFirst();
        List<String> table = follow.getSecond();
        if (!followType.equals("TABLE")) {
            error("Error table does not follow data " + words.get(0) + " " + words.get(1));
            return;
        }
        if (dataNames.containsKey(className)) {
            className += stepCount;
            warning("Data name is duplicated, has been renamed " + className);
        }
        trace("Creating class for " + className);
        dataNames.put(className, "");
        // Put each in a new file
        startDataFile(className, false);

        dataPrintLn("package " + packagePath + ";");
        for (String line : linesToAddForDataAndGlue) {
            dataPrintLn(line);
        }
        addSuppressionOfWarnings();
        dataPrintLn("class " + className + "{");
        List<DataValues> variables = new ArrayList<>();
        boolean doInternal = createVariableList(table, variables);
        for (DataValues variable : variables) {
            classDataNames.add(className + "#" + variable.name);
            dataPrintLn("    String " + makeName(variable.name) + " = \"" + variable.defaultVal + "\";");
        }
        createConstructor(variables, className);
        createEqualsMethod(variables, className);
        createBuilderMethod(variables, className);
        createToStringMethod(variables, className);
        createToJSONMethod(variables);
        createFromJSONMethod(variables, className);
        createTableToJSONMethod(className);
        createJSONToTableMethod(className);
        if (doInternal) {
            dataNamesInternal.put(internalClassName, "");
            createConversionMethod(internalClassName, variables);
        }
        dataPrintLn("    }");
        endDataFile();

        if (doInternal) {
            createInternalClass(internalClassName, className, variables, providedOtherClassName);
        }
    }

    private void createJSONToTableMethod(String className) {
        String code =
                """
                                     public static List<CLASSNAME> listFromJson(String json) {
                                            List<CLASSNAME> list = new ArrayList<>();
                                    		json = json.replaceAll("\\\\s", "");
                                    		json = json.replaceAll("\\\\[","").replaceAll("]","");
                                            String[] jsonObjects = json.split("(?<=\\\\}),\\\\s*(?=\\\\{)");
                                            for (String jsonObject : jsonObjects) {
                                                 list.add(CLASSNAME.fromJson(jsonObject));
                                                 }
                                            return list;
                                        }
                        """.stripIndent();
        code = code.replace("CLASSNAME", className);
        dataPrintLn(code);
    }

    private void createTableToJSONMethod(String className) {
        String code =
                """
                                     public static String listToJson(List<CLASSNAME> list) {
                                         StringBuilder jsonBuilder = new StringBuilder();
                                         jsonBuilder.append("[");
                                                         
                                         for (int i = 0; i < list.size(); i++) {
                                             jsonBuilder.append(list.get(i).toJson());
                                             if (i < list.size() - 1) {
                                                 jsonBuilder.append(",");
                                             }
                                         }
                                                         
                                         jsonBuilder.append("]");
                                         return jsonBuilder.toString();
                                     }
                        """.stripIndent();
        code = code.replace("CLASSNAME", className);
        dataPrintLn(code);
    }

    private void endDataFile() {
        if (Configuration.oneDataFile)
            return;
        try {

            dataDefinitionFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void endOneDataFile() {
        if (!Configuration.oneDataFile)
            return;
        if (!oneDataFileStarted)
            return;
        try {
            dataDefinitionFile.close();
            oneDataFileStarted = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startDataFile(String className, boolean createTmpl) {
        if (Configuration.oneDataFile) {
            startOneDataFile();
            return;
        }
        String extension = Configuration.dataDefinitionFileExtension;
        if (createTmpl)
            extension = "tmpl";
        String dataDefinitionPathname = Configuration.testSubDirectory + featureDirectory +
                featureName + "/" + className
                + "." + extension;
        try {
            dataDefinitionFile = new FileWriter(dataDefinitionPathname, false);
        } catch (IOException e) {
            error("IO Exception in setting up the files");
            error(" Writing " + dataDefinitionPathname);

        }
    }

    static boolean oneDataFileStarted = false;
    private void startOneDataFile() {
        if (oneDataFileStarted)
            return;
        oneDataFileStarted = true;
        String extension = Configuration.dataDefinitionFileExtension;

        String dataDefinitionPathname = Configuration.testSubDirectory + featureDirectory +
                featureName + "/" + featureName + "_data"
                + "." + extension;
        try {
            dataDefinitionFile = new FileWriter(dataDefinitionPathname, false);
        } catch (IOException e) {
            error("IO Exception in setting up the files");
            error(" Writing " + dataDefinitionPathname);

        }
    }

    private void createConstructor(List<DataValues> variables, String className) {
        dataPrintLn("    public " + className + "() { }");
        dataPrintLn("    public " + className + "(");
        String comma = "";
        for (DataValues variable : variables) {
            dataPrintLn("        " + comma + "String " + makeName(variable.name));
            comma = ",";
        }
        dataPrintLn("        ){");
        for (DataValues variable : variables) {
            dataPrintLn("        this." + makeName(variable.name) + " = " + makeName(variable.name) + ";");
        }
        dataPrintLn("        }");
    }

    private void createInternalConstructor(List<DataValues> variables, String className) {
        dataPrintLn("    public " + className + "(");
        String comma = "";
        for (DataValues variable : variables) {
            dataPrintLn("        " + comma + variable.dataType + " " + makeName(variable.name));
            comma = ",";
        }
        dataPrintLn("        ) " + " {");
        for (DataValues variable : variables) {
            dataPrintLn("        this." + makeName(variable.name) + " = " + variable.name + ";");
        }
        dataPrintLn("        }");
    }


    private void createToStringMethod(List<DataValues> variables, String className) {
        StringBuilder code = new StringBuilder();
        String firstPart =
                """
                                @Override
                                public String toString() {
                                    return "CLASSNAME {"
                        """.stripIndent();
        code.append(firstPart.replace("CLASSNAME", className));

        for (DataValues variable : variables) {
            String middlePart =
                    """
                                         +"NAME = " + NAME + " "
                            """.stripIndent();
            code.append(middlePart.replaceAll("NAME", variable.name));
        }
        String endPart =
                """  
                                     + "} " ; }
                        """.stripIndent();
        if (Configuration.addLineToString)
            endPart =
                    """  
                                         + "} " + "\\n"; }
                            """.stripIndent();
        code.append(endPart);
        dataPrintLn(code.toString());
    }

    private void createFromJSONMethod(List<DataValues> variables, String className) {
        String firstPart =
                """
                                public static CLASSNAME fromJson(String json) {
                                      CLASSNAME instance = new CLASSNAME();
                                                         
                                      	json = json.replaceAll("\\\\s", "");
                                        String[] keyValuePairs = json.replace("{", "").replace("}", "").split(",");
                                                            
                                        // Iterate over the key-value pairs
                                        for (String pair : keyValuePairs) {
                                            // Split each pair by the colon
                                            String[] entry = pair.split(":");
                                                            
                                            // Remove the quotes from the key and value
                                            String key = entry[0].replace("\\"", "").trim();
                                            String value = entry[1].replace("\\"", "").trim();
                                                            
                                 
                                  // Assign the value to the corresponding field
                                            switch (key) {
                        """.stripIndent();
        firstPart = firstPart.replace("CLASSNAME", className);
        StringBuilder middlePart = new StringBuilder();
        for (DataValues variable : variables) {
            String start =
                    """
                                          case "NAME":
                                              instance.NAME = value;
                                              break;
                            """.stripIndent();
            String toAdd = start.replace("NAME", variable.name);
            middlePart.append(toAdd);
        }

        String lastPart =
                """
                                				default:\s
                                				    System.err.println("Invalid JSON element " + key);\s
                                            }
                                        }
                                        return instance;
                                    }
                                                        
                        """.stripIndent();
        dataPrintLn(firstPart + middlePart + lastPart);
    }


    private void createToJSONMethod(List<DataValues> variables) {
        StringBuilder code = new StringBuilder();
        String firstPart =
                """
                            public String toJson() {
                                return " {"
                        """.stripIndent();
        code.append(firstPart);
        String comma = "";
        for (DataValues variable : variables) {
            String middlePart =
                    """
                                     +""+"NAME:" + "\\"" + NAME + "\\""
                            """.stripIndent();
            code.append(comma);
            comma = "         + \",\"";
            code.append(middlePart.replaceAll("NAME", variable.name));
        }
        String lastPart =
                """
                                + "} " ; }
                """.stripIndent();
        code.append(lastPart);
        dataPrintLn(code.toString());
    }

    private void createBuilderMethod(List<DataValues> variables, String className) {
        addSuppressionOfWarnings();
        dataPrintLn("    public static class Builder {");
        for (DataValues variable : variables) {
            dataPrintLn("        private String " + variable.name + " = " + quoteIt(variable.defaultVal) + ";");
        }
        for (DataValues variable : variables) {
            dataPrintLn("        public Builder " + makeBuildName(variable.name) + "(String " + variable.name + ") {");
            dataPrintLn("            this." + variable.name + " = " + variable.name + ";");
            dataPrintLn("            return this;");
            dataPrintLn("            }");
        }
        dataPrintLn("        public " + "Builder " + " setCompare() {");
        for (DataValues variable : variables) {
            dataPrintLn("            " + variable.name + " = " + quoteIt(Configuration.doNotCompare) + ";");
        }
        dataPrintLn("            return this;");
        dataPrintLn("            }");

        dataPrintLn("        public " + className + " build(){");
        dataPrintLn("             return new " + className + "(");
        String comma = "";
        for (DataValues variable : variables) {
            dataPrintLn("                 " + comma + variable.name);
            comma = ",";
        }
        dataPrintLn("                );   } ");
        dataPrintLn("        } ");
    }

    private void addSuppressionOfWarnings() {
        dataPrintLn("//noinspection CanBeFinal");
        dataPrintLn("//noinspection UnusedImports");
        dataPrintLn("@SuppressWarnings({\"unused\", \"EnhancedSwitchMigration\", \"ClassCanBeRecord\", \"NewClassNamingConvention\", \"RedundantSuppression\"})");
    }

    private void createEqualsMethod(List<DataValues> variables, String className) {
        dataPrintLn("    @Override");
        dataPrintLn("    public boolean equals (Object o) {");
        dataPrintLn("        if (this == o) return true;");
        dataPrintLn("        if (o == null || getClass() != o.getClass())");
        dataPrintLn("             return false;");

        String variableName = "_" + className;
        dataPrintLn("        " + className + " " + variableName + " = (" + className + ") o;");
        dataPrintLn("            boolean result = true;");
        for (DataValues variable : variables) {
            dataPrintLn("         if (");
            dataPrintLn("             !this." + variable.name + ".equals(" + quoteIt(Configuration.doNotCompare) + ")");
            dataPrintLn("                && !" + variableName + "." + variable.name + ".equals(" + quoteIt(Configuration.doNotCompare) + "))");
            dataPrintLn("                if (! " + variableName + "." + variable.name + ".equals(this." + variable.name + ")) result = false;");
        }
        dataPrintLn("             return result;  }");
    }

    private void createConversionMethod(String internalClassName, List<DataValues> variables) {
        dataPrintLn("    " + internalClassName + " to" + internalClassName + "() " + throwString + "{");
        dataPrintLn("        return new " + internalClassName + "(");
        String comma = "";
        for (DataValues variable : variables) {
            String initializer = makeValueFromString(variable, true);
            dataPrintLn("        " + comma + " " + initializer);
            comma = ",";
        }
        dataPrintLn("        ); }"); // end function

    }


    private boolean createVariableList(List<String> table, List<DataValues> variables) {
        boolean headerLine = true;
        boolean doInternal = false;
        for (String line : table) {
            if (headerLine) {
                List<String> headers = parseLine(line);
                checkHeaders(headers);
                headerLine = false;

                if (headers.size() > 2) doInternal = true;
                continue;
            }
            List<String> elements = parseLine(line);
            if (elements.size() < 2) {
                error(" Data line has less than 2 entries " + line);
                continue;
            }
            if (elements.size() > 3)
                variables.add(new DataValues(makeName(elements.get(0)), elements.get(1), alterDataType(elements.get(2)), elements.get(3)));
            else if (elements.size() > 2)
                variables.add(new DataValues(makeName(elements.get(0)), elements.get(1), alterDataType(elements.get(2))));
            else variables.add(new DataValues(makeName(elements.get(0)), elements.get(1)));
        }
        return doInternal;
    }

    private String alterDataType(String s) {
        // Will need to be altered to language specific types
        switch (s) {
            case "Int":
                return "Integer";
            case "Char":
                return "Character";
            case "Decimal":
                return "Double";
            case "string":
                return "String";
            case "Text":
                return "String";
            default:
                return s;
        }
    }

    private void checkHeaders(List<String> headers) {
        List<String> expected = List.of("Name", "Default", "Datatype", "Notes");
        if (!(headers.get(0).equals(expected.get(0)) && headers.get(1).equals(expected.get(1)))) {
            error("Headers should start with " + expected);
        }
    }

    private void createInternalClass(String className, String otherClassName, List<DataValues> variables,
                                     boolean providedClassName) {
        String classNameInternal = className;
        if (dataNames.containsKey(classNameInternal)) {
            classNameInternal += stepCount;
            warning("Data name is duplicated, has been renamed " + classNameInternal);
        }
        trace("Creating internal class for " + classNameInternal);
        dataNames.put(classNameInternal, "");
        startDataFile(className, providedClassName);
        dataPrintLn("package " + packagePath + ";");
        for (String line : linesToAddForDataAndGlue) {
            dataPrintLn(line);
        }
        addSuppressionOfWarnings();
        dataPrintLn("class " + className + "{");
        for (DataValues variable : variables) {
            dataPrintLn("     " + variable.dataType + " " + makeName(variable.name) + ";");
        }
        dataPrintLn("     ");
        createDataTypeToStringObject(className, variables);
        createToStringObject(otherClassName, variables);
        createInternalConstructor(variables, className);
        createInternalEqualsMethod(variables, className);
        createToStringMethod(variables, className);

        dataPrintLn("    }"); // end class
        endDataFile();
    }

    private void createInternalEqualsMethod(List<DataValues> variables, String className) {
        dataPrintLn("    @Override");
        dataPrintLn("    public boolean equals (Object o) {");
        dataPrintLn("        if (this == o) return true;");
        dataPrintLn("        if (o == null || getClass() != o.getClass()) return false;");

        String variableName = "_" + className;
        dataPrintLn("        " + className + " " + variableName + " = (" + className + ") o;");
        dataPrintLn("         return ");
        String and = "";
        for (DataValues variable : variables) {
            String comparison = ".equals";
            if (primitiveDataType(variable))
                comparison = " == ";
            dataPrintLn("                " + and + "( " + variableName + "." + variable.name + comparison + "(this." + variable.name + "))");
            and = " && ";
        }
        dataPrintLn("             ;  }");

    }

    private boolean primitiveDataType(DataValues variable) {
        return (variable.dataType.equals("boolean"))
                || (variable.dataType.equals("char"))
                || (variable.dataType.equals("int"))
                || (variable.dataType.equals("float"))
                || (variable.dataType.equals("double"))
                || (variable.dataType.equals("long"))
                || (variable.dataType.equals("byte"))
                || (variable.dataType.equals("short"));

    }


    private void createDataTypeToStringObject(String className, List<DataValues> variables) {
        dataPrintLn("    public static String toDataTypeString() {");
        dataPrintLn("        return " + quoteIt(className + " {"));
        String add = "+";
        String space = " ";
        for (DataValues variable : variables) {
            dataPrintLn("        " + add + quoteIt(variable.dataType + space) + " ");
        }
        dataPrintLn("            + " + quoteIt("} ") + "; }  ");
    }

    private void createToStringObject(String otherClassName, List<DataValues> variables) {
        dataPrintLn("    " + otherClassName + " to" + otherClassName + "() " + "{");
        dataPrintLn("        return new " + otherClassName + "(");
        String comma = "";
        for (DataValues variable : variables) {
            String method = makeValueToString(variable, true);
            dataPrintLn("        " + comma + method);
            comma = ",";
        }
        dataPrintLn("        ); }"); // end function
    }

    @SuppressWarnings("SameParameterValue")
    private String makeValueToString(DataValues variable, boolean makeNameValue) {
        String value;
        if (makeNameValue)
            value = makeName(variable.name);
        else
            value = quoteIt(variable.defaultVal);
        switch (variable.dataType) {
            case "String":
                return value;
            case "int":
            case "double":
            case "byte":
            case "short":
            case "long":
            case "float":
            case "boolean":
            case "char":
            case "Byte":
            case "Short":
            case "Integer":
            case "Long":
            case "Float":
            case "Double":
            case "Boolean":
            case "Character":
                return "String.valueOf(" + value + ")";
            default:
                return value + ".toString()";

        }
    }


    private void dataPrintLn(String line) {
        try {
            dataDefinitionFile.write(line);
            dataDefinitionFile.write("\n");
        } catch (IOException e) {
            error("IO ERROR");
        }
    }
}

private class ImportConstruct {

    @SuppressWarnings("unused")
    class ImportData {
        public final String dataType;
        public final String importName;
        public final String conversionMethod;
        public final String notes;

        public ImportData(String dataType, String conversionMethod, String importName, String notes) {
            this.dataType = dataType;
            this.importName = importName;
            this.conversionMethod = conversionMethod;
            this.notes = notes;
        }

        public ImportData(String dataType, String conversionMethod, String importName) {
            this(dataType, conversionMethod, importName, "");
        }

        public ImportData(String dataType, String conversionMethod) {

            this(dataType, conversionMethod, "", "");
        }
    }

    private void actOnImport(List<String> words) {
        Pair<String, List<String>> follow = lookForFollow();
        String followType = follow.getFirst();
        List<String> table = follow.getSecond();
        if (!followType.equals("TABLE")) {
            error("Error table does not follow import " + words.get(0) + " " + words.get(1));
            return;
        }
        List<ImportData> imports = new ArrayList<>();
        createImportList(table, imports);
        for (ImportData im : imports) {
            if (importNames.containsKey(im.dataType)) {
                error("Data type is duplicated, has been renamed " + im.dataType);
                continue;
            }
            if (!im.conversionMethod.isEmpty())
                importNames.put(im.dataType, im.conversionMethod);
            else {
                String methodName = im.dataType + "($)";
                importNames.put(im.dataType, methodName);
            }

        }
        for (ImportData im : imports) {
            if (!im.importName.isEmpty()) {
                String value = "import " + im.importName + ";";
                linesToAddForDataAndGlue.add(value);
            }
        }
    }

    private void createImportList(List<String> table, List<ImportData> variables) {
        boolean headerLine = true;
        for (String line : table) {
            if (headerLine) {
                List<String> headers = parseLine(line);
                checkHeaders(headers);
                headerLine = false;
                continue;
            }
            List<String> elements = parseLine(line);
            if (elements.size() < 2) {
                error(" Data line has less than 2 entries " + line);
                continue;
            }
            if (elements.size() > 3)
                variables.add(new ImportData(elements.get(0), elements.get(1), elements.get(2), elements.get(3)));
            else if (elements.size() > 2)
                variables.add(new ImportData(elements.get(0), elements.get(1), elements.get(2)));
            else variables.add(new ImportData(elements.get(0), elements.get(1)));
        }

    }

    private void checkHeaders(List<String> headers) {
        List<String> expected = List.of("Datatype", "ConversionMethod", "Import", "Notes");
        if (!(headers.get(0).equals(expected.get(0)) && headers.get(1).equals(expected.get(1)))) {
            error("Headers should start with " + expected);
        }
    }


}

private class DefineConstruct {

    class DefineData {
        public final String name;
        public final String value;

        public DefineData(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return " name = " + name + " value = " + value;
        }

    }

    private void actOnDefine(List<String> words) {
        Pair<String, List<String>> follow = lookForFollow();
        String followType = follow.getFirst();
        List<String> table = follow.getSecond();
        if (!followType.equals("TABLE")) {
            error("Error table does not follow define " + words.get(0) + " " + words.get(1));
            return;
        }
        List<DefineData> defines = new ArrayList<>();
        createDefineList(table, defines);
        for (DefineData im : defines) {
            if (defineNames.containsKey(im.name)) {
                warning("Define is duplicated will be skipped " + im.name + " = " + im.value);
                continue;
            }
            if (im.value.isEmpty()) {
                warning("Empty value for define ");
            }
            defineNames.put(im.name, im.value);
        }
    }

    private void createDefineList(List<String> table, List<DefineData> variables) {
        boolean headerLine = true;
        for (String line : table) {
            if (headerLine) {
                List<String> headers = parseLine(line);
                checkHeaders(headers);
                headerLine = false;
                continue;
            }
            List<String> elements = parseLine(line);
            if (elements.size() < 2) {
                error(" Data line has less than 2 entries " + line);
            } else variables.add(new DefineData(elements.get(0), elements.get(1)));
        }

    }

    private void checkHeaders(List<String> headers) {
        List<String> expected = List.of("Name", "Value", "Notes");
        if (!(headers.get(0).equals(expected.get(0)) && headers.get(1).equals(expected.get(1)))) {
            error("Headers should start with " + expected);
        }
    }


}

@SuppressWarnings({"unused", "EmptyClassInitializer"})
static class Configuration {
    public static boolean logIt = false;
    // Set to true for logging during the tests to log.txt
    public static boolean inTest = false;
    // switch to true for development of Translator
    public static boolean traceOn = false;
    // Set to true to see trace
    public static final char spaceCharacters = '~';
    // Will replace this character with space in tables
    public static final boolean addLineToString = true;
    // add a \n to the toString method
    public static final String doNotCompare = "?DNC?";
    // Value used for not comparing an attribute
    public static String currentDirectory = "";
    // To keep for testing and or setup issues
    public static final String featureSubDirectory = "src/test/java/";
    // where features are stored
    public static final String treeDirectory = "features/";
    public static final String startingFeatureDirectory = featureSubDirectory + treeDirectory;
    // where the directory tree of features is to be found.
    public static boolean searchTree = false;
    // search the startingFeatureDirectory for feature files
    public static final String packageName = "gherkinexecutor";
    // high level package in which the tests are placed
    public static final String testSubDirectory = "src/test/java/" + packageName + "/";
    // used to put the test files in the directory corresponding to the packageName.
    public static final String dataDefinitionFileExtension = "java"; // "tmpl";
    // change to tmpl if you are altering the data classes to avoid overwriting them
    public static final String testFramework = "JUnit5";
    // Could be "JUnit4" or "TestNG"
    public static String addToPackageName = "";
    // change to "test.java." for Eclipse
    public static final List<String> linesToAddForDataAndGlue = new ArrayList<>();
    public static String filterExpression =""; // will hold filter expression from command line or file

    // Imports or other lines to add to data class and glue class
    // Must include  semicolon if needed
    static {
        linesToAddForDataAndGlue.add("import java.util.*;"); // as an example
    }

    public static final List<String> featureFiles = new ArrayList<>();

    static {
//            featureFiles.add("simple_test.featurex");     // Something to try out after setup
//            featureFiles.add("full_test.feature.sav"); // used for testing Translate
    }
    public static String tagFilter = "";
    // expression to determine which scenarios to code

    public static boolean oneDataFile = false ;  // All data into one file
        // This should not be set to true for Java
}




 static class TagFilterEvaluator {
    // Author is Microsoft CoPilot

     public static boolean shouldNotExecute(List<String> words, String filterExpression){
         Set<String> scenarioTags = new HashSet<> (words);
         return (!shouldExecute(scenarioTags, filterExpression));
     }
     public static boolean shouldExecute(Set<String> scenarioTags, String filterExpression) {
         if (filterExpression.trim().isEmpty())
             return true;
         List<Set<String>> requiredConditions = new ArrayList<>();
         Set<String> excludedTags = new HashSet<>();

         // Parse the expression into required and excluded conditions
         parseExpression(filterExpression, requiredConditions, excludedTags);

         // Check if scenario contains any excluded tags
         boolean hasExcludedTag = scenarioTags.stream().anyMatch(excludedTags::contains);

         // Check if scenario matches any required condition group (OR logic)
         boolean matchesRequired = requiredConditions.isEmpty() ||
                 requiredConditions.stream().anyMatch(scenarioTags::containsAll);

         // Execute if it meets a required condition AND does NOT have an excluded tag
         return matchesRequired && !hasExcludedTag;
     }

     private static void parseExpression(String expression, List<Set<String>> requiredConditions, Set<String> excludedTags) {
         // Split by "OR" to get groups
         String[] groups = expression.split(" OR ");
         for (String group : groups) {
             Set<String> tags = new HashSet<>();

             // Split each group by "AND"
             String[] elements = group.trim().split(" AND ");
             for (String element : elements) {
                 element = element.trim();
                 if (element.startsWith("NOT ")) {
                     excludedTags.add(element.replace("NOT ", "")); // Store excluded tags
                 } else {
                     tags.add(element); // Store required tags
                 }
             }

             if (!tags.isEmpty()) {
                 requiredConditions.add(tags);
             }
         }
     }
 }
}




