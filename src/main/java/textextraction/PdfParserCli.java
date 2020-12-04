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
   * The path to the file to which the content of the PDF document should be serialized (if set to
   * null, the output is written to stdout).
   */
  @Arg(dest = "serialTargetFile")
  protected String serialFileStr = null;

  /**
   * The serialization format (e.g., xml or json).
   */
  @Arg(dest = "serialFormat")
  protected String serialFormatStr = "json";

  /**
   * The path to the file to which a visualization of the content elements extracted from the PDF
   * document should be written.
   */
  @Arg(dest = "visualTargetFile")
  protected String visualFileStr = null;

  /**
   * The element classes to extract.
   */
  @Arg(dest = "elementClasses")
  protected List<String> elementClassStrs = new ArrayList<>(ElementClass.getNames());

  // ==============================================================================================

  /**
   * Runs the PDF parser.
   * 
   * @param args The command line arguments.
   */
  protected void run(String[] args) {
    // Parse the command line arguments.
    parseCommandLineArguments(args);

    // Translate the arguments (given as strings) to the respective objects.
    Path inputFile = Paths.get(this.inputFileStr);
    Path serialFile = this.serialFileStr != null ? Paths.get(this.serialFileStr) : null;
    Path visualFile = this.visualFileStr != null ? Paths.get(this.visualFileStr) : null;
    SerializationFormat serialFormat = SerializationFormat.fromString(this.serialFormatStr);
    Collection<ElementClass> elementClasses = ElementClass.fromStrings(this.elementClassStrs);

    try {
      // Parse the PDF.
      PdfDocument pdf = parsePdf(inputFile);
      // Serialize the PDF.
      serializePdf(pdf, serialFile, serialFormat, elementClasses);
      // Visualize the PDF.
      visualizePdf(pdf, visualFile, elementClasses);
    } catch (PdfParserException | SerializerException | VisualizerException e) {
      LOG.error("An error occurred on processing '{}'.", inputFile, e);
    }
  }

  /**
   * Parses the given command line arguments.
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

    // Add an argument to define the serialization target file.
    arg = parser.addArgument("outputFile");
    arg.help("The output file. If not specified, the output will be written to stdout.");
    arg.dest("serialTargetFile");
    arg.nargs("?");
    arg.setDefault(serialFileStr);
    arg.metavar("<output>");

    // Add an argument to define the serialization format.
    String choicesStr = "[" + String.join(", ", SerializationFormat.getNames()) + "]";
    arg = parser.addArgument("-f", "--format");
    arg.help("The output format. Choose from: " + choicesStr + ".");
    arg.dest("outputFormat");
    arg.nargs("?");
    arg.choices(SerializationFormat.getNames());
    arg.setDefault(serialFormatStr);
    arg.metavar("<format>");

    // Add an argument to define the visualization target file.
    arg = parser.addArgument("-v", "--visualization");
    arg.help("The path to a file to which a visualization of the extracted content elements should "
            + "be written. If not specified, no visualization will be created.");
    arg.dest("visualTargetFile");
    arg.nargs("?");
    arg.setDefault(visualFileStr);
    arg.metavar("<visualization>");

    // Add an argument to define the types of elements to serialize and to visualize.
    choicesStr = "[" + String.join(", ", ElementClass.getNames()) + "]";
    arg = parser.addArgument("-t", "--type");
    arg.help("The types of the elements to extract. Choose from: " + choicesStr + ".");
    arg.dest("elementClasses");
    arg.nargs("*");
    arg.choices(ElementClass.getNames());
    arg.setDefault(elementClassStrs);
    arg.metavar("<type>", "<type>");

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
   * @param path The PDF file to parse.
   * 
   * @return The parsed PDF document.
   * 
   * @throws PdfParserException If something went wrong on parsing the PDF file.
   */
  protected PdfDocument parsePdf(Path pdf) throws PdfParserException {
    return new PdfParser().parse(pdf);
  }

  /**
   * Serializes the given PDF file to the given output file in the given format.
   * 
   * @param pdf        The PDF document to serialize.
   * @param outputFile The file to write the serialization to.
   * @param format     The serialization format.
   * @param classes    The types of elements to serialize.
   * 
   * @throws PdfParserException If something went wrong on serializing the PDF document.
   */
  protected void serializePdf(PdfDocument pdf, Path outputFile, SerializationFormat format,
          Collection<ElementClass> classes) throws SerializerException {
    // Serialize the PDF document.
    byte[] serialization = new DocumentSerializer().serialize(pdf, format, classes);

    // Write the serialized PDF document to file (or stdout).
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
   * Visualizes the elements of the given PDF file to the given output file.
   * 
   * @param pdf        The PDF document to visualize.
   * @param outputFile The file to write the visualization to.
   * @param classes    The types of elements to visualize.
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

