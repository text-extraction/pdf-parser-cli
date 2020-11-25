package textextraction;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import textextraction.serializer.JsonSerializer;
import textextraction.serializer.Serializer;
import textextraction.serializer.XmlSerializer;
import textextraction.serializer.exception.SerializerException;
import textextraction.serializer.exception.model.SerializationFormat;

/**
 * The main class to run the PDF parser from the command line.
 * 
 * @author Claudius Korzen
 */
public class PdfParserCLI {
  /**
   * The logger.
   */
  protected static final Logger LOG = LogManager.getFormatterLogger(PdfParserCLI.class);

  /**
   * The path to the PDF file to process.
   */
  @Arg(dest = "inputFile")
  protected String inputFile;

  /**
   * The path to the output file (if set to null, the output is written to stdout).
   */
  @Arg(dest = "outputFile")
  protected String outputFile = null;

  /**
   * The output format (e.g., xml or json).
   */
  @Arg(dest = "outputFormat")
  protected String outputFormat = "json";

  /**
   * The element types to extract.
   */
  @Arg(dest = "elementTypes")
  protected List<String> elementClazzes = new ArrayList<>(ElementClass.getNames());

  // ==============================================================================================

  /**
   * Runs the PDF parser.
   * 
   * @param args The command line arguments.
   */
  protected void run(String[] args) {
    // Parse the command line arguments.
    parseCommandLineArguments(args);

    Path inputFile = Paths.get(this.inputFile);
    Path outputFile = this.outputFile != null ? Paths.get(this.outputFile) : null;
    SerializationFormat outputFormat = SerializationFormat.fromString(this.outputFormat);
    Set<ElementClass> elementClazzes = ElementClass.fromStrings(this.elementClazzes);

    try {
      // Parse the PDF.
      PdfDocument pdf = parsePdf(inputFile);
      // Serialize the PDF.
      serializePdf(pdf, outputFile, outputFormat, elementClazzes);
    } catch (PdfParserException | SerializerException e) {
      LOG.error("An error occurred on parsing '{}'.", inputFile, e);
    }
  }

  /**
   * Parses the given command line arguments.
   * 
   * @param args The command line arguments to parse.
   */
  protected void parseCommandLineArguments(String[] args) {
    // Build the argument parser.
    ArgumentParserBuilder builder = ArgumentParsers.newFor(PdfParserCLI.class.getSimpleName());
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

    // Add an argument to define the output file.
    arg = parser.addArgument("outputFile");
    arg.help("The output file. If not specified, the output will be written to stdout.");
    arg.dest("outputFile");
    arg.nargs("?");
    arg.setDefault(outputFile);
    arg.metavar("<output>");

    // Add an argument to define the output format.
    String choicesStr = "[" + String.join(", ", SerializationFormat.getNames()) + "]";
    arg = parser.addArgument("-f", "--format");
    arg.help("The output format. Choose from: " + choicesStr + ".");
    arg.dest("outputFormat");
    arg.nargs("?");
    arg.choices(SerializationFormat.getNames());
    arg.setDefault(outputFormat);
    arg.metavar("<format>");

    // Add an argument to define the types of elements to extract.
    choicesStr = "[" + String.join(", ", ElementClass.getNames()) + "]";
    arg = parser.addArgument("-t", "--type");
    arg.help("The types of the elements to extract. Choose from: " + choicesStr + ".");
    arg.dest("elementTypes");
    arg.nargs("*");
    arg.choices(ElementClass.getNames());
    arg.setDefault(elementClazzes);
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
   * @param clazzes    The types of elements to serialize.
   * 
   * @throws PdfParserException If something went wrong on serializing the PDF document.
   */
  protected void serializePdf(PdfDocument pdf, Path outputFile, SerializationFormat format,
          Set<ElementClass> clazzes) throws SerializerException {
    // Choose the correct serializer.
    Serializer serializer;
    switch (format) {
      case XML:
        serializer = new XmlSerializer();
        break;
      case JSON:
      default:
        serializer = new JsonSerializer();
        break;
    }

    // Serialize the PDF document.
    byte[] serialization = serializer.serialize(pdf, clazzes);

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

  // ==============================================================================================

  /**
   * The main method.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    new PdfParserCLI().run(args);
  }
}

