/*
 * Copyright (c) 2025 derreisende77.
 * This code was developed as part of the MediathekView project https://github.com/mediathekview/MediathekView
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.tool.ttml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Converter for TTML XML subtitle files into SubRip Text format. Tested with MediathekView
 * downloaded subtitles and TTML format version 1.0.
 */
public class TimedTextMarkupLanguageParser implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(TimedTextMarkupLanguageParser.class);
    private final SimpleDateFormat assFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final SimpleDateFormat ttmlFormat = new SimpleDateFormat("HH:mm:ss.SS");
    private final SimpleDateFormat srtFormat = new SimpleDateFormat("HH:mm:ss,SSS");
    private final SimpleDateFormat sdfFlash = new SimpleDateFormat("s.S");
    private final Map<String, Integer> alignMap = new HashMap<>();
    private final Map<String, List<String>> colorMap = new HashMap<>();
    private final Map<String, Integer> regionMap = new HashMap<>();
    private final List<Subtitle> subtitleList = new ArrayList<>();
    private final String backgroundColor = "#000000C2";
    private String color = "#FFFFFF";
    private Document doc;

    public TimedTextMarkupLanguageParser() {
    }

    /**
     * Convert a {@link Color} into a BGR hex string
     */
    private String colorToBGR(Color color) {
        return String.format("%02X%02X%02X", color.getBlue(), color.getGreen(), color.getRed());
    }

    /**
     * Converts a hex string to a {@link Color}. If it can't be converted null is returned.
     *
     * @param hex (i.e. #CCCCCCFF or CCCCCC)
     * @return Color
     */
    private Color hexToColor(@NotNull String hex) {
        // based on https://stackoverflow.com/a/43764322
        hex = hex.replace("#", "");
        if (hex.length() == 6) {
            return new Color(
                    Integer.valueOf(hex.substring(0, 2), 16),
                    Integer.valueOf(hex.substring(2, 4), 16),
                    Integer.valueOf(hex.substring(4, 6), 16));
        } else if (hex.length() == 8) {
            return new Color(
                    Integer.valueOf(hex.substring(0, 2), 16),
                    Integer.valueOf(hex.substring(2, 4), 16),
                    Integer.valueOf(hex.substring(4, 6), 16),
                    Integer.valueOf(hex.substring(6, 8), 16));
        }
        // return a default white color if something failed
        logger.error("Failed to convert hex color string: {}", hex);
        return Color.WHITE;
    }

    /**
     * Build a map of used alignments within the TTML file.
     */
    private void buildAlignmentMap() {
        final NodeList styleData = doc.getElementsByTagName("tt:style");
        for (int i = 0; i < styleData.getLength(); i++) {
            final Node subnode = styleData.item(i);
            if (subnode.hasAttributes()) {
                final NamedNodeMap attrMap = subnode.getAttributes();
                final Node idNode = attrMap.getNamedItem("xml:id");
                final Node alignmentNode = attrMap.getNamedItem("tts:textAlign");
                if (idNode != null && alignmentNode != null) {
                    final int alignment = convertAlignment(alignmentNode.getNodeValue());
                    alignMap.put(idNode.getNodeValue(), alignment);
                }
            }
        }
    }

    /**
     * Convert the text alignment to int:
     * <p>start: -1
     *
     * <p>left: -1
     *
     * <p>center: 0
     *
     * <p>right: 1
     *
     * <p>end: 1
     *
     * @param text the text representation
     * @return an int of the text alignment.
     */
    protected int convertAlignment(@NotNull String text) {
        return switch (text) {
            case "start", "left" -> -1;
            case "right", "end" -> 1;
            default -> 0;
        };
    }

    /**
     * Build a map of used colors within the TTML file.
     */
    private void buildColorMap() {
        final NodeList styleData = doc.getElementsByTagName("tt:style");
        for (int i = 0; i < styleData.getLength(); i++) {
            final Node subnode = styleData.item(i);
            if (subnode.hasAttributes()) {
                final NamedNodeMap attrMap = subnode.getAttributes();
                final Node idNode = attrMap.getNamedItem("xml:id");
                final Node colorNode = attrMap.getNamedItem("tts:color");
                final Node colorBackgroundNode = attrMap.getNamedItem("tts:backgroundColor");
                if (idNode != null && colorNode != null) {
                    final List<String> colorList = new ArrayList<>();
                    colorList.add(colorNode.getNodeValue());
                    if (colorBackgroundNode != null) {
                        colorList.add(colorBackgroundNode.getNodeValue());
                    } else {
                        colorList.add(backgroundColor);
                    }
                    colorMap.put(idNode.getNodeValue(), colorList);
                }
            }
        }
    }

    /**
     * Build a map of used screen regions within the TTML file.
     *
     * <p>This ignores the tts:origin and tts:extent attributes for now and solely decides position
     * mapping on tts:displayAlign.
     */
    private void buildRegionMap() {
        final NodeList styleData = doc.getElementsByTagName("tt:region");
        for (int i = 0; i < styleData.getLength(); i++) {
            final Node subnode = styleData.item(i);
            if (subnode.hasAttributes()) {
                final NamedNodeMap attrMap = subnode.getAttributes();
                final Node idNode = attrMap.getNamedItem("xml:id");
                final Node regionNode = attrMap.getNamedItem("tts:displayAlign");
                if (idNode != null && regionNode != null) {
                    final var region = convertRegion(regionNode.getNodeValue());
                    regionMap.put(idNode.getNodeValue(), region);
                }
            }
        }
    }

    /**
     * Convert the region text to int value.
     *
     * <p>ASS format aligns to numbers as seen on the numpad on a keyboard.
     * <br/>Possible positions:
     * <br/>7 <b>8</b> 9
     * <br/>4 <b>5</b> 6
     * <br/>1 <b>2</b> 3
     *
     * <p>Defaults to bottom center (2).
     *
     * @param text the text value.
     * @return the int value.
     */
    protected int convertRegion(@NotNull String text) {
        return switch (text) {
            case "before" -> 8;
            case "center" -> 5;
            default -> 2;
        };
    }

    private void checkHours(@NotNull Date date) {
        // HACK:: Don´t know why this is set like this...
        // but we have to subract 10 hours from the XML
        final int hours = date.getHours();
        if (hours >= 10) {
            date.setHours(hours - 10);
        }
    }

    /**
     * Build the Subtitle objects from TTML content.
     */
    private void buildFilmList() throws Exception {
        final NodeList subtitleData = doc.getElementsByTagName("tt:p");

        for (int i = 0; i < subtitleData.getLength(); i++) {
            final Subtitle subtitle = new Subtitle();

            final Node subnode = subtitleData.item(i);
            if (subnode.hasAttributes()) {
                // retrieve the begin and end attributes...
                final NamedNodeMap attrMap = subnode.getAttributes();
                final Node beginNode = attrMap.getNamedItem("begin");
                final Node endNode = attrMap.getNamedItem("end");
                if (beginNode != null && endNode != null) {
                    subtitle.begin = ttmlFormat.parse(beginNode.getNodeValue());
                    checkHours(subtitle.begin);

                    subtitle.end = ttmlFormat.parse(endNode.getNodeValue());
                    checkHours(subtitle.end);
                }

                final Node regionNode = attrMap.getNamedItem("region");
                final Node styleNode = attrMap.getNamedItem("style");
                final Integer alignment =
                        (styleNode != null && alignMap.containsKey(styleNode.getNodeValue()))
                                ? alignMap.get(styleNode.getNodeValue())
                                : 0;
                final Integer region =
                        (regionNode != null && regionMap.containsKey(regionNode.getNodeValue()))
                                ? regionMap.get(regionNode.getNodeValue())
                                : 2;
                subtitle.region = Integer.toString(alignment + region);
            }

            final NodeList childNodes = subnode.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                final Node node = childNodes.item(j);
                if (node.getNodeName().equalsIgnoreCase("tt:span")) {
                    // retrieve the text and color information...
                    final NamedNodeMap attrMap = node.getAttributes();
                    final Node styleNode = attrMap.getNamedItem("style");

                    final StyledString textContent = new StyledString();
                    textContent.setText(node.getTextContent());

                    final List<String> colors =
                            (styleNode != null && colorMap.containsKey(styleNode.getNodeValue()))
                                    ? colorMap.get(styleNode.getNodeValue())
                                    : null;
                    if (colors == null) {
                        textContent.setColor(color); // gabs beim BR
                        textContent.setBackgroundColor(backgroundColor);
                    } else {
                        textContent.setColor(colors.get(0));
                        textContent.setBackgroundColor(colors.get(1));
                    }
                    subtitle.listOfStrings.add(textContent);
                }
            }
            subtitleList.add(subtitle);
        }
    }

    private Date parseFlash(String tStamp) throws ParseException {
        Date da;
        if (tStamp.contains(":")) {
            da = ttmlFormat.parse(tStamp);
        } else {
            da = sdfFlash.parse(tStamp + "00");
        }
        return da;
    }

    /**
     * Build the Subtitle objects from TTML content.
     */
    private void buildFilmListFlash() throws Exception {
        final NodeList subtitleData = doc.getElementsByTagName("p");

        for (int i = 0; i < subtitleData.getLength(); i++) {
            final Subtitle subtitle = new Subtitle();

            final Node subnode = subtitleData.item(i);
            if (subnode.hasAttributes()) {
                // retrieve the begin and end attributes...
                final NamedNodeMap attrMap = subnode.getAttributes();
                final Node beginNode = attrMap.getNamedItem("begin");
                final Node endNode = attrMap.getNamedItem("end");
                if (beginNode != null && endNode != null) {
                    subtitle.begin = parseFlash(beginNode.getNodeValue());
                    subtitle.end = parseFlash(endNode.getNodeValue());
                    final StyledString textContent =
                            new StyledString(subnode.getTextContent(), color, backgroundColor);

                    final Node col = attrMap.getNamedItem("tts:color");
                    if (col != null) {
                        textContent.setColor(col.getNodeValue());
                    } else {
                        final NodeList childNodes = subnode.getChildNodes();
                        for (int j = 0; j < childNodes.getLength(); j++) {
                            final Node node = childNodes.item(j);
                            if (node.getNodeName().equalsIgnoreCase("span")) {
                                // retrieve the text and color information...
                                final NamedNodeMap attr = node.getAttributes();
                                final Node co = attr.getNamedItem("tts:color");
                                textContent.setColor(co.getNodeValue());
                            }
                        }
                    }
                    subtitle.listOfStrings.add(textContent);
                }
            }
            subtitleList.add(subtitle);
        }
    }

    private DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder();
    }

    /**
     * Parse the TTML file into internal representation.
     *
     * @param ttmlFilePath the TTML file to parse
     */
    public boolean parse(Path ttmlFilePath) {
        boolean ret;
        try {
            doc = getDocumentBuilder().parse(ttmlFilePath.toFile());

            // Check that we have TTML v1.0 file as we have tested only them...
            final NodeList metaData = doc.getElementsByTagName("ebuttm:documentEbuttVersion");
            if (metaData != null) {
                final Node versionNode = metaData.item(0);
                if (versionNode == null || !versionNode.getTextContent().equalsIgnoreCase("v1.0")) {
                    throw new Exception("Unknown TTML file version");
                }
            } else {
                throw new Exception("Unknown File Format");
            }

            buildAlignmentMap();
            buildColorMap();
            buildRegionMap();
            buildFilmList();
            ret = true;
        } catch (Exception ex) {
            logger.error("File: {}", ttmlFilePath, ex);
            ret = false;
        }
        return ret;
    }

    /**
     * Parse the XML Subtitle File for Flash Player into internal representation.
     *
     * @param ttmlFilePath the TTML file to parse
     * @return true if successful
     */
    public boolean parseXmlFlash(Path ttmlFilePath) {
        boolean ret;
        try {
            doc = getDocumentBuilder().parse(ttmlFilePath.toFile());

            // Check that we have TTML v1.0 file as we have tested only them...
            final NodeList metaData = doc.getElementsByTagName("tt");
            final NodeList colorNote = doc.getElementsByTagName("style");
            if (metaData != null) {
                final Node node = metaData.item(0);

                if (node.hasAttributes()) {
                    // retrieve the begin and end attributes...
                    final NamedNodeMap attrMap = node.getAttributes();
                    final Node xmlns = attrMap.getNamedItem("xmlns");
                    if (xmlns != null) {
                        final String s = xmlns.getNodeValue();
                        if (!s.equals("http://www.w3.org/2006/04/ttaf1")
                                && !s.equals("http://www.w3.org/ns/ttml")) {
                            throw new Exception("Unknown TTML file version");
                        }
                    }
                } else {
                    throw new Exception("Unknown File Format");
                }
            } else {
                throw new Exception("Unknown File Format");
            }
            if (colorNote != null) {
                if (colorNote.getLength() == 0) {
                    this.color = "#FFFFFF";
                } else {
                    final Node node = colorNote.item(0);

                    if (node.hasAttributes()) {
                        // retrieve the begin and end attributes...
                        final NamedNodeMap attrMap = node.getAttributes();
                        final Node col = attrMap.getNamedItem("tts:color");
                        if (col != null) {
                            if (!col.getNodeValue().isEmpty()) {
                                this.color = col.getNodeValue();
                            }
                        }
                    } else {
                        throw new Exception("Unknown File Format");
                    }
                }
            } else {
                throw new Exception("Unknown File Format");
            }
            buildFilmListFlash();
            ret = true;
        } catch (Exception ex) {
            logger.error("File: {}", ttmlFilePath, ex);
            ret = false;
        }
        return ret;
    }

    /**
     * Trim milliseconds to deciseconds, SimpleDateFormat does not provide this functionality
     */
    private String getAssTime(Date time) {
        final String assTime = assFormat.format(time);
        return assTime.substring(0, assTime.length() - 1);
    }

    /**
     * Convert internal representation into Advanced Substation Alpha Format and save to file.
     *
     * <p>References:
     *
     * <p>- <a href="https://github.com/libass/libass/wiki/ASS-File-Format-Guide">ASS File Format Guide</a>
     *
     * <p>- <a href="https://aegisub.org/docs/latest/styles/#the-style-editor">Aegisub Style Editor</a>
     *
     * <p>- <a href="https://aegisub.org/docs/latest/ass_tags/">Aegisub ASS tags</a>
     */
    public void toAss(Path assFile) {
        try (FileOutputStream fos = new FileOutputStream(assFile.toFile());
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {
            writer.print(
                    """
                            [Script Info]
                            ; Script generated by MediathekView
                            ScriptType: v4.00+
                            ScaledBorderAndShadow: yes
                            YCbCr Matrix: None
                            
                            [V4+ Styles]
                            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                            Style: Default,,22,,,,,0,0,0,0,100,100,0,0,3,1,0,2,10,10,25,1
                            
                            [Events]
                            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                            """);
            for (Subtitle title : subtitleList) {
                final String titleRegion = title.region;
                final String titleBegin = getAssTime(title.begin);
                final String titleEnd = getAssTime(title.end);

                String titleText = "Dialogue: 0," + titleBegin + "," + titleEnd + ",Default,,0,0,0,,";

                if (titleRegion != null) {
                    // \an = alignment
                    // alignment always affects the whole ASS dialogue line
                    titleText += "{\\an" + titleRegion + "}";
                }

                StringBuilder entryText = new StringBuilder();
                for (var entry : title.listOfStrings) {
                    // Assume new lines between same title entries, check if there's content already present
                    if (!entryText.isEmpty()) {
                        entryText.append("\\N");
                    }

                    // start formatting options
                    entryText.append("{");

                    final String entryColorString = entry.getColor();
                    if (!entryColorString.isEmpty()) {
                        // \1c = primary fill color
                        entryText.append("\\1c&H")
                                .append(colorToBGR(hexToColor(entryColorString)))
                                .append("&");
                    }

                    final String entryBackgroundColorString = entry.getBackgroundColor();
                    if (!entryBackgroundColorString.isEmpty()) {
                        final Color entryBackgroundColor = hexToColor(entryBackgroundColorString);
                        // \3c = border color
                        entryText.append("\\3c&H")
                                .append(colorToBGR(entryBackgroundColor))
                                .append("&");

                        // \3a = border alpha
                        // The value is inverted to regular RGBA alpha
                        entryText.append("\\3a&H")
                                .append(String.format("%02X", 255 - entryBackgroundColor.getAlpha()))
                                .append("&");
                    }

                    // end formatting options
                    entryText.append("}");

                    // lastly add actual subtitle text
                    // ORF inline line break tags, replace them when found
                    entryText.append(entry.getText().replace("<br/>", "\\N"));
                }
                writer.println(titleText + entryText);
            }
        } catch (Exception ex) {
            logger.error("File: {}", assFile, ex);
        }
    }

    /**
     * Convert internal representation into SubRip Text Format and save to file.
     */
    public void toSrt(Path srtFile) {
        try (FileOutputStream fos = new FileOutputStream(srtFile.toFile());
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {
            long counter = 1;
            for (Subtitle title : subtitleList) {
                writer.println(counter);
                writer.println(srtFormat.format(title.begin) + " --> " + srtFormat.format(title.end));
                for (var entry : title.listOfStrings) {
                    final var entryColor = entry.getColor();
                    if (!entryColor.isEmpty()) {
                        writer.print("<font color=\"" + entryColor + "\">");
                    }
                    writer.print(entry.getText());
                    if (!entryColor.isEmpty()) {
                        writer.print("</font>");
                    }
                    writer.println();
                }
                writer.println("");
                counter++;
            }
        } catch (Exception ex) {
            logger.error("File: {}", srtFile, ex);
        }
    }

    @Override
    public void close() {
        colorMap.clear();
        subtitleList.clear();
    }
}
