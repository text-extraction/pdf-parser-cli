package textextraction;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.sourceforge.argparse4j.ArgumentParserBuilder;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import textextraction.common.models.ElementClass;
import textextraction.pdfparser.PdfParser;
import textextraction.pdfparser.exception.PdfParserException;
import textextraction.pdfparser.model.PdfDocument;
import textextraction.serializer.DocumentSerializer;
import textextraction.serializer.exception.SerializerException;
import textextraction.serializer.model.SerializationFormat;
import textextraction.visualizer.DocumentVisualizer;
import textextraction.visualizer.exception.VisualizerException;

/**
 * The main class to run the PDF parser from the command line.
 * 
 * @author Claudius Korzen
 */
public class PdfParserCli {
  /**
   * The logger.
   */
  protected static final Logger LOG = LogManager.getFormatterLogger(PdfParserCli.class);

  /**
   * The path to the PDF file to process.
   */
  @Arg(dest = "inputFile")
  protected String inputFileStr;

  /**
   * The path to the file to which the content elements extracted from the PDF document should be
   * serialized (if set to null, the output is written to stdout).
   */
  @Arg(dest = "serialFile")
  protected String serialFileStr = null;

  /**
   * The serialization format (e.g., xml or json).
   */
  @Arg(dest = "serialFormat")
  protected String serialFormatStr = "json";

  /**
   * The path to the file to which the content elements extracted from the PDF document should be
   * visualized (if set to null, no visualization will be created).
   */
  @Arg(dest = "visualFile")
  protected String visualFileStr = null;

  /**
   * The element classes to serialize and visualize.
   */
  @Arg(dest = "elementClasses")
  protected List<String> elementClassesStrs = new ArrayList<>(ElementClass.getNames());

  // ==============================================================================================

  /**
   * Runs the PDF parser.
   * 
   * @param args The command line arguments.
   */
  protected void run(String[] args) {
    // Parse the command line arguments.
    parseCommandLineArguments(args);

    // Translate the arguments (given as strings) to their respective objects.
    Path inputFile = Paths.get(this.inputFileStr);
    Path serialFile = this.serialFileStr != null ? Paths.get(this.serialFileStr) : null;
    Path visualFile = this.visualFileStr != null ? Paths.get(this.visualFileStr) : null;
    SerializationFormat serialFormat = SerializationFormat.fromString(this.serialFormatStr);
    Collection<ElementClass> elementClasses = ElementClass.fromStrings(this.elementClassesStrs);

    try {
      // Parse the PDF.
      PdfDocument pdf = parsePdf(inputFile);
      // Serialize the content elements extracted from the PDF.
      serializePdf(pdf, serialFile, serialFormat, elementClasses);
      // Visualize the content elements extracted from the PDF.
      visualizePdf(pdf, visualFile, elementClasses);
    } catch (PdfParserException | SerializerException | VisualizerException e) {
      LOG.error("An error occurred on processing '{}'.", inputFile, e);
    }
  }

  /**
   * Builds an argument parser and uses this parser to parse the given command line arguments.
   * 
   * @param args The command line arguments to parse.
   */
  protected void parseCommandLineArguments(String[] args) {
    // Build the argument parser.
    ArgumentParserBuilder builder = ArgumentParsers.newFor(PdfParserCli.class.getSimpleName());
    builder.terminalWidthDetection(false);
    builder.defaultFormatWidth(100);

    // Add a description.
    ArgumentParser parser = builder.build();
    parser.description("A tool for parsing the content streams of a PDF file.");

    // Add an argument to define the PDF file to process.
    Argument arg = parser.addArgument("inputFile");
    arg.help("The PDF file to process.");
    arg.dest("inputFile");
    arg.required(true);
    arg.metavar("<pdf>");

    // Add an argument to define the target file for the serialization.
    arg = parser.addArgument("outputFile");
    arg.help("The output file. If not specified, the output will be written to stdout.");
    arg.dest("serialFile");
    arg.nargs("?");
    arg.setDefault(serialFileStr);
    arg.metavar("<output>");

    // Add an argument to define the serialization format.
    String choicesStr = "[" + String.join(", ", SerializationFormat.getNames()) + "]";
    arg = parser.addArgument("-f", "--format");
    arg.help("The output format. Choose from: " + choicesStr + ".");
    arg.dest("serialFormat");
    arg.nargs("?");
    arg.choices(SerializationFormat.getNames());
    arg.setDefault(serialFormatStr);
    arg.metavar("<format>");

    // Add an argument to define the target file for the visualization.
    arg = parser.addArgument("-v", "--visualization");
    arg.help("The path to a file to which a visualization of the extracted content elements should "
            + "be written. If not specified, no visualization will be created.");
    arg.dest("visualFile");
    arg.nargs("?");
    arg.setDefault(visualFileStr);
    arg.metavar("<visualization>");

    // Add an argument to define the element classes to serialize and visualize.
    choicesStr = "[" + String.join(", ", ElementClass.getNames()) + "]";
    arg = parser.addArgument("-c", "--class");
    arg.help("The element classes to extract. Choose from: " + choicesStr + ".");
    arg.dest("elementClasses");
    arg.nargs("*");
    arg.choices(ElementClass.getNames());
    arg.setDefault(elementClassesStrs);
    arg.metavar("<class>", "<class>");

    try {
      // Parse the command line arguments.
      parser.parseArgs(args, this);
    } catch (HelpScreenException e) {
      // The help screen was requested, so print the help screen.
      System.out.println(parser.formatHelp());
      System.exit(0);
    } catch (ArgumentParserException e) {
      // There is an invalid use of the arguments. Print the usage info.
      System.err.println(e.getMessage());
      System.err.println(parser.formatUsage());
      System.exit(1);
    }
  }

  // ==============================================================================================

  /**
   * Parses the given PDF file.
   * 
   * @param pdf The PDF file to parse.
   * 
   * @return The parsed PDF document.
   * 
   * @throws PdfParserException If something went wrong on parsing the PDF file.
   */
  protected PdfDocument parsePdf(Path pdf) throws PdfParserException {
    return new PdfParser().parse(pdf);
  }

  /**
   * Serializes the given PDF document in the given format and writes the serialization to the given
   * output file.
   * 
   * @param pdf        The PDF document to serialize.
   * @param outputFile The target file for the serialization.
   * @param format     The serialization format.
   * @param classes    The element classes to serialize.
   * 
   * @throws SerializerException If something went wrong on serializing the PDF document.
   */
  protected void serializePdf(PdfDocument pdf, Path outputFile, SerializationFormat format,
          Collection<ElementClass> classes) throws SerializerException {
    // Serialize the PDF document.
    byte[] serialization = new DocumentSerializer().serialize(pdf, format, classes);

    // Write the serialized PDF document either to file or stdout.
    if (outputFile != null) {
      try (OutputStream os = Files.newOutputStream(outputFile)) {
        os.write(serialization);
      } catch (IOException e) {
        throw new SerializerException("Couldn't write the serialization to file.", e);
      }
    } else {
      try {
        System.out.write(serialization);
      } catch (IOException e) {
        throw new SerializerException("Couldn't write the serialization to stdout.", e);
      }
    }
  }

  /**
   * Creates a visualization of the PDF document, that is: a PDF file in which each content element
   * of the given PDF document is highlighted by its bounding box. Writes the visualization to the
   * given output file.
   * 
   * @param pdf        The PDF document to visualize.
   * @param outputFile The target file for the visualization.
   * @param classes    The element classes to visualize.
   * 
   * @throws VisualizerException If something went wrong on visualizing the PDF document.
   */
  protected void visualizePdf(PdfDocument pdf, Path outputFile, Collection<ElementClass> classes)
          throws VisualizerException {
    // Visualize the PDF document.
    byte[] visualization = new DocumentVisualizer().visualize(pdf, classes);

    // Write the visualization to file.
    try (OutputStream os = Files.newOutputStream(outputFile)) {
      os.write(visualization);
    } catch (IOException e) {
      throw new VisualizerException("Couldn't write the visualization to file.", e);
    }
  }

  // ==============================================================================================

  /**
   * The main method.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    new PdfParserCli().run(args);
  }
}

