package IC;

import IC.AST.ICClass;
import IC.AST.PrettyPrinter;
import IC.AST.Program;
import IC.Parser.*;
import IC.Semantic.BreakContinueAndThisValidator;
import IC.Semantic.SemanticError;
import IC.Semantic.SemanticScopeChecker;
import IC.Semantic.SingleMainFunctionValidator;
import IC.Symbols.GlobalSymbolTable;
import IC.Symbols.SymbolTable;
import IC.Symbols.SymbolTableBuilderVisitor;

import java.io.*;

import java_cup.runtime.Symbol;

public class Compiler {
	public static void main(String[] args) {
		// check that received one parameter
		try {
			// Reads all options and file paths and makes sure they exist.
			Options options = Options.parseCommandLineArgs(args);

			// Parses the library signature file.
			Symbol libParseSymbol = parseLibraryFile(options.libicPath);
			if (libParseSymbol == null) {
				// Parsing failed. Errors have been printed.
				System.exit(0);
			}
			ICClass libraryClass = (ICClass) libParseSymbol.value;

			// Parses the IC source file.
			Symbol parseSymbol = parseICFile(options.sourcePath);
			if (parseSymbol == null) {
				// Parsing failed. Errors have been printed.
				System.exit(0);
			}
			Program program = (Program) parseSymbol.value;

			if (options.printAST) {
				// If asked in the command line, pretty-print the program
				// (and the Library signature file) to System.out.
				printAST(options, libraryClass, program);
			}

			if (!doSemanticChecks(options, libraryClass, program,
					options.sourcePath)) {
				System.out.println();
				System.out
						.println("Semantic errors occurred, compilation terminated!");
			}

		} catch (IOException e) {
			// We were asked to gracefully return 0 on errors.
			System.out.println(e);
			System.exit(0);
		}
	}

	private static boolean doSemanticChecks(Options options,
			ICClass libraryClass, Program program, String filepath)
			throws IOException {
		// try {
		// ///////////////////////////
		// Semantic checks start here
		boolean hadErrors = false;
		// 1. Add the Library class as an actual class for simplicity.
		program.getClasses().add(0, libraryClass);

		// 2. Build the Symbol Table and Type Table.
		SymbolTableBuilderVisitor symTabBuilder = new SymbolTableBuilderVisitor(
				new File(options.sourcePath).getName());
		GlobalSymbolTable symbolTable = symTabBuilder.visit(program);
		for (SemanticError error : symTabBuilder.getErrors()) {
			printError(filepath, error);
			hadErrors = true;
		}

		//
		// (1) scope rules (Section 10 in the IC specification);

		SemanticScopeChecker scopeChecker = new SemanticScopeChecker();
		scopeChecker.visit(program);
		for (SemanticError error : scopeChecker.getErrors()) {
			printError(filepath, error);
			hadErrors = true;
		}

		// (2) type-checking rules (Section 15), including a check that
		// the class hierarchy is a tree and checking
		// correct overriding of instance methods in subclasses;
		// (3) checking that the program contains a single main method
		// with the correct signature;
		SingleMainFunctionValidator mainValidator = new SingleMainFunctionValidator();
		SemanticError mainValidatorResult = (SemanticError) mainValidator
				.visit(program);
		if (mainValidatorResult != null) {
			printError(filepath, mainValidatorResult);
			hadErrors = true;
		}
		// (4) that break and continue statements appear only inside
		// loops;
		// (5) that the this keyword is only used in instance methods;
		// and
		BreakContinueAndThisValidator keywordValid = new BreakContinueAndThisValidator();
		keywordValid.visit(program);
		for (SemanticError error : keywordValid.getErrors()) {
			printError(filepath, error);
			hadErrors = true;
		}
		// (6) that the library class has the correct name (Library).
		// This is done in the Syntax analysis.

		if (!hadErrors && options.dumpSymTab) {
			System.out.println();
			System.out.println(symbolTable.toString());
		}
		return !hadErrors;
	}

	private static void printAST(Options options, ICClass libraryClass,
			Program root) {
		PrettyPrinter libPrinter = new PrettyPrinter(options.libicPath,
				new OutputStreamWriter(System.out));
		libPrinter.visit(libraryClass);
		PrettyPrinter printer = new PrettyPrinter(options.sourcePath,
				new OutputStreamWriter(System.out));
		printer.visit(root);
		System.out.println();
	}

	private static Symbol parseLibraryFile(String libicSigPath)
			throws IOException {
		Reader libSigFile = new FileReader(libicSigPath);
		Lexer lexer = new Lexer(libSigFile);
		LibraryParser parser = new LibraryParser(lexer);
		return runParser(parser, libicSigPath, "library file " + libicSigPath);
	}

	private static Symbol parseICFile(String filepath) throws IOException {
		Reader icSourceFile = new FileReader(filepath);
		Lexer lexer = new Lexer(icSourceFile);
		Parser parser = new Parser(lexer);
		return runParser(parser, filepath, filepath);
	}

	private static Symbol runParser(java_cup.runtime.lr_parser parser,
			String filepath, String displayFilepath) throws IOException {
		try {
			Symbol parseSymbol = parser.parse();
			System.out.println("Parsed " + displayFilepath + " successfully!");
			return parseSymbol;
		} catch (LexicalError e) {
			printError(filepath, e, "Lexical error while parsing "
					+ displayFilepath + ".");
			return null;
		} catch (SyntaxError e) {
			printError(filepath, e, "Syntax error while parsing "
					+ displayFilepath + ".");
			return null;
		} catch (Exception e) {
			// Not supposed to get here because our parser only throws
			// SyntaxError
			System.out.println("Unexpected error while parsing "
					+ displayFilepath + ".");
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}

	private static void printError(String filepath, ICCompilerError e,
			String message) throws IOException {
		System.out.println(message);
		System.out.println(e);
		printLine(filepath, e.getLine());
	}

	private static void printError(String filepath, ICCompilerError e)
			throws IOException {
		System.out.println();
		System.out.println(e);
		printLine(filepath, e.getLine());
	}

	private static void printLine(String filepath, int line) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(filepath));
		int currentLine = 0;
		String strLine;
		while ((strLine = in.readLine()) != null) {
			if (++currentLine == line) {
				// Print the content on the console
				System.out.println("Line " + line + ": " + strLine);
				break;
			}
		}
	}

	static class Options {
		private String libicPath;
		private String sourcePath;
		private boolean printAST;
		private boolean dumpSymTab;

		private Options() {
			this.libicPath = "libic.sig";
			this.printAST = false;
			this.sourcePath = null;
		}

		private static void handleWrongSyntax() {
			System.out.println("Can't run compiler.");
			System.out
					.println("Usage:\n\tjava IC.Compiler <file.ic> [ -L</path/to/libic.sig> ] [ -print-ast ] [ -dump-symtab ]");
			System.exit(1);
		}

		public static Options parseCommandLineArgs(String[] args) {
			Options options = new Options();
			if (args.length == 0) {
				handleWrongSyntax();
			}

			for (String arg : args) {
				if (arg.startsWith("-L")) {
					options.libicPath = arg.substring(2);
				} else if (arg.equals("-print-ast")) {
					options.printAST = true;
				} else if (arg.equals("-dump-symtab")) {
					options.dumpSymTab = true;
				} else if (!arg.startsWith("-") && options.sourcePath == null) {
					options.sourcePath = arg;
				} else {
					System.out.println("Unrecognized flag: " + arg);
					handleWrongSyntax();
				}
			}

			if (options.sourcePath == null) {
				System.out.println("Source file isn't specified.");
				handleWrongSyntax();
			}
			options.makeSureValid();
			return options;
		}

		private void makeSureValid() {
			File f = new File(libicPath);
			boolean valid = true;
			if (!f.exists()) {
				System.out
						.println("Can't find library signature file at path: "
								+ libicPath);
				valid = false;
			}
			f = new File(sourcePath);
			if (!f.exists()) {
				System.out.println("Can't find source file at path: "
						+ sourcePath);
				valid = false;
			}
			if (!valid) {
				handleWrongSyntax();
			}
		}
	}
}