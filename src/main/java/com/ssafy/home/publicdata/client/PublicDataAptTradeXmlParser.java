package com.ssafy.home.publicdata.client;

import com.ssafy.home.publicdata.dto.AptTradeApiItem;
import com.ssafy.home.publicdata.dto.AptTradeApiResponse;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class PublicDataAptTradeXmlParser {

    public AptTradeApiResponse parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            int totalCount = parseInt(text(document.getDocumentElement(), "totalCount"), 0);
            NodeList itemNodes = document.getElementsByTagName("item");
            List<AptTradeApiItem> items = new ArrayList<>();
            for (int index = 0; index < itemNodes.getLength(); index++) {
                Element item = (Element) itemNodes.item(index);
                items.add(new AptTradeApiItem(
                        text(item, "sggCd"),
                        text(item, "umdNm"),
                        text(item, "jibun"),
                        text(item, "aptNm"),
                        text(item, "buildYear"),
                        text(item, "dealYear"),
                        text(item, "dealMonth"),
                        text(item, "dealDay"),
                        text(item, "dealAmount"),
                        text(item, "excluUseAr"),
                        text(item, "floor"),
                        text(item, "aptDong"),
                        text(item, "buyerGbn"),
                        text(item, "slerGbn"),
                        text(item, "dealingGbn"),
                        text(item, "estateAgentSggNm"),
                        text(item, "cdealType"),
                        text(item, "cdealDay"),
                        text(item, "rgstDate"),
                        text(item, "landLeaseholdGbn")
                ));
            }
            return new AptTradeApiResponse(totalCount, items);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse public data XML response", exception);
        }
    }

    private static String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }
}
