package main.java.pl.dawidstepien.pdfbox;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

public class Main {

  private static int fileNameCounter = 1;

  public static void main(String[] args) throws IOException {
    File srcPDf = new File("/home/dstepien/tmp/src_document.pdf");
    File destPdf = new File("/home/dstepien/tmp/dest_document.pdf");

    try (PDDocument document = new PDDocument()) {
      Stream.iterate(0, n -> n++)
        .limit(3)
        .forEach(n -> document.addPage(new PDPage()));
      saveDocument(document, srcPDf);
    }

    try (PDDocument document = PDDocument.load(srcPDf)) {
      PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(0));

      contentStream.beginText();
      contentStream.newLineAtOffset(25, 700);
      contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
      contentStream.setLeading(14.5F);
      contentStream.showText("This is an example of adding text to a page in the pdf document. "
        + "We can add as many lines");
      contentStream.newLine();
      contentStream.showText("as we want like this using the ShowText()  method of the ContentStream class.");
      contentStream.endText();

      contentStream.setNonStrokingColor(Color.DARK_GRAY);
      contentStream.addRect(300, 550, 100, 100);
      contentStream.fill();

      PDImageXObject pdImage = PDImageXObject.createFromFile("/home/dstepien/tmp/logo.png", document);
      contentStream.drawImage(pdImage, 70, 600);

      contentStream.close();

      AccessPermission accessPermission = new AccessPermission();
      accessPermission.setCanPrint(false);
      StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy("12345", "12345", accessPermission);
      protectionPolicy.setEncryptionKeyLength(128);
      document.protect(protectionPolicy);

      String javaScript = "app.alert( {cMsg: 'this is an example', nIcon: 3,"
        + " nType: 0,cTitle: 'PDFBox Javascript example' } );";
      PDActionJavaScript actionJavaScript = new PDActionJavaScript(javaScript);
      document.getDocumentCatalog().setOpenAction(actionJavaScript);

      Splitter splitter = new Splitter();
      List<PDDocument> documents = splitter.split(document);

      PDFMergerUtility merger = new PDFMergerUtility();
      merger.setDestinationFileName("/home/dstepien/tmp/dest_document_merged.pdf");

      int counter = 1;
      for (PDDocument pageDocument : documents) {
        File file = new File(String.format("/home/dstepien/tmp/dest_document_%s.pdf", counter++));
        saveDocument(pageDocument, file);
        merger.addSource(file);
        pageDocument.close();
      }

      merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
      saveDocument(document, destPdf);
    }

    try (PDDocument document = PDDocument.load(destPdf, "12345")) {
      PDFTextStripper pdfStripper = new PDFTextStripper();
      String text = pdfStripper.getText(document);
      System.out.println("PDF's text:");
      System.out.println(text);
      System.out.println("Rendering page as image...");
      PDFRenderer renderer = new PDFRenderer(document);
      BufferedImage image = renderer.renderImage(0);
      ImageIO.write(image, "JPEG", new File("/home/dstepien/tmp/rendered_page.jpg"));
    }

    try (PDDocument document = PDDocument.load(destPdf, "12345")) {
      PDPageTree pages = document.getDocumentCatalog().getPages();
      for (PDPage page : pages) {
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();
//        tokens.forEach(System.out::println);
        for (int i = 0; i < tokens.size(); i++) {
          Object token = tokens.get(i);
          if (token instanceof Operator) {
            Operator currentToken = (Operator) token;
            if (Objects.equals(currentToken.getName(), "Do")) {
              COSName image = (COSName) tokens.get(i - 1);
              page.getCOSObject().removeItem(image);
              PDImageXObject imageObject = (PDImageXObject) page.getResources().getXObject(image);
              imageObject.getMetadata();
            }
            if (Objects.equals(currentToken.getName(), "Tj")) {
              COSString previousToken = (COSString) tokens.get(i - 1);
              String text = previousToken.getString();
              System.out.println("Replacing document");
              previousToken.setValue(text.replace("example", "EXAMPLE").getBytes());
            }
          }
        }
        PDStream pdStream = new PDStream(document);
        OutputStream out = pdStream.createOutputStream();
        ContentStreamWriter contentStreamWriter = new ContentStreamWriter(out);
        contentStreamWriter.writeTokens(tokens);
        page.setContents(pdStream);
        out.close();
      }
      document.setAllSecurityToBeRemoved(true);
      saveDocument(document, destPdf);
    }
  }

  private static void saveDocument(PDDocument document, File pdf) throws IOException {
    PDDocumentInformation info = document.getDocumentInformation();
    info.setAuthor("Dawid Stępień");
    info.setCreationDate(GregorianCalendar.from(ZonedDateTime.now()));
    info.setModificationDate(GregorianCalendar.from(ZonedDateTime.now()));
    info.setCreator("PDFBox");
    info.setKeywords("one two three");
    info.setSubject("Lorem ipsum");
    info.setTitle("Java program pdf");

    document.save(pdf);
    System.out.println("PDF created (" + pdf.getName() + ")");
    System.out.println("Number of pages: " + document.getNumberOfPages());
  }
}
