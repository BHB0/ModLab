/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.assistant;

import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.blockly.data.ExternalTrigger;
import net.mcreator.blockly.data.ToolboxBlock;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.blockly.BlocklyEditorType;
import net.mcreator.ui.init.L10N;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The list of procedure blocks of the current Minecraft version, built from the files of the generator. It is used to
 * tell the assistant which blocks really exist, and to check the blocks it proposes before they are added.
 */
public final class BlockCatalog {

	private static final Logger LOG = LogManager.getLogger("Block catalog");

	private static final String BLOCKLY_NAMESPACE = "https://developers.google.com/blockly/xml";

	/** Standard blocks of the editor that are allowed besides the blocks of the generator. */
	private static final Set<String> STANDARD_BLOCKS = Set.of("controls_if", "controls_repeat_ext",
			"controls_whileUntil", "logic_compare", "logic_operation", "logic_negate", "logic_boolean", "logic_ternary",
			"math_number", "math_arithmetic", "math_single", "text", "text_join", "text_length");

	public record Entry(String type, String category, String label, String example, boolean output) {}

	public record Validation(boolean ok, String xml, List<String> unknown) {}

	private static List<Entry> cache;
	private static String cacheKey;

	private static synchronized List<Entry> entries(MCreator mcreator) {
		String key = String.valueOf(mcreator.getGeneratorConfiguration().getGeneratorName());
		if (cache != null && key.equals(cacheKey))
			return cache;

		List<Entry> list = new ArrayList<>();
		try {
			for (ToolboxBlock block : BlocklyLoader.INSTANCE.getBlockLoader(BlocklyEditorType.PROCEDURE)
					.getDefinedBlocks().values()) {
				String machine = block.getMachineName();
				String label = L10N.t("blockly.block." + machine);
				if (label.startsWith("blockly.block."))
					label = machine;
				String category = block.getToolboxCategory() != null ? block.getToolboxCategory().getName() : "";
				String example = block.getToolboxXML() == null ?
						"" :
						block.getToolboxXML().replaceAll("\\s+", " ").strip();
				if (example.length() > 360)
					example = example.substring(0, 360) + "...";
				list.add(new Entry(machine, category, label.replaceAll("%\\d+", "_"), example,
						block.getType().name().equals("OUTPUT")));
			}
		} catch (Exception e) {
			LOG.warn("Could not read the blocks", e);
		}

		cache = list;
		cacheKey = key;
		return list;
	}

	/**
	 * @return text for the system prompt: the most relevant blocks for the question, with an example of each
	 */
	public static String contextFor(MCreator mcreator, String question) {
		List<Entry> all = entries(mcreator);
		StringBuilder sb = new StringBuilder();

		Map<String, Integer> categories = new TreeMap<>();
		for (Entry entry : all)
			categories.merge(entry.category(), 1, Integer::sum);
		sb.append("Procedure blocks available in this Minecraft version: ").append(all.size()).append(". Categories: ");
		categories.forEach((name, count) -> sb.append(name).append(" (").append(count).append("), "));
		sb.append("\n\n");

		Set<String> tokens = tokens(question);
		List<Entry> relevant = all.stream().map(e -> Map.entry(score(e, tokens), e)).filter(e -> e.getKey() > 0)
				.sorted((a, b) -> b.getKey() - a.getKey()).limit(30).map(Map.Entry::getValue).toList();

		if (!relevant.isEmpty()) {
			sb.append("Blocks that look relevant (type = machine name, then category, label, and an example of its XML):\n");
			for (Entry entry : relevant) {
				sb.append("- ").append(entry.type()).append(" | ").append(entry.category()).append(" | ")
						.append(entry.label()).append(entry.output() ? " | returns a value" : " | action")
						.append(" | ").append(entry.example()).append("\n");
			}
		}

		try {
			List<String> triggers = new ArrayList<>();
			for (ExternalTrigger trigger : BlocklyLoader.INSTANCE.getExternalTriggerLoader(BlocklyEditorType.PROCEDURE)
					.getExternalTriggers()) {
				String name = L10N.t("trigger." + trigger.getID());
				String text = (trigger.getID() + " " + name).toLowerCase(Locale.ROOT);
				if (tokens.stream().anyMatch(text::contains))
					triggers.add(trigger.getID() + " = " + name);
			}
			if (!triggers.isEmpty())
				sb.append("\nTriggers that look relevant: ").append(String.join("; ", triggers.subList(0, Math.min(10, triggers.size())))).append("\n");
		} catch (Exception e) {
			LOG.debug("No triggers", e);
		}

		return sb.toString();
	}

	private static Set<String> tokens(String question) {
		Set<String> tokens = new LinkedHashSet<>();
		for (String word : question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+")) {
			if (word.length() >= 3)
				tokens.add(word.length() > 5 ? word.substring(0, word.length() - 1) : word); // rough plural/verb ending
		}
		return tokens;
	}

	private static int score(Entry entry, Set<String> tokens) {
		String text = (entry.type() + " " + entry.category() + " " + entry.label()).toLowerCase(Locale.ROOT);
		int score = 0;
		for (String token : tokens)
			if (text.contains(token))
				score++;
		return score;
	}

	/**
	 * Checks the XML given by the assistant: well-formed, and every block exists. Nothing is added to the editor if a
	 * block is unknown.
	 */
	public static Validation validate(MCreator mcreator, String xml) {
		Set<String> known = new HashSet<>(STANDARD_BLOCKS);
		for (Entry entry : entries(mcreator))
			known.add(entry.type());

		String cleaned = xml.strip();
		if (Pattern.compile("^<xml(\\s[^>]*)?>").matcher(cleaned).find() && !cleaned.matches("(?s)^<xml[^>]*xmlns[^>]*>.*"))
			cleaned = cleaned.replaceFirst("^<xml", "<xml xmlns=\"" + BLOCKLY_NAMESPACE + "\"");

		List<String> unknown = new ArrayList<>();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setExpandEntityReferences(false);
			Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(cleaned)));

			if (!"xml".equals(document.getDocumentElement().getTagName()))
				return new Validation(false, cleaned, List.of("<" + document.getDocumentElement().getTagName() + ">"));

			for (String tag : new String[] { "block", "shadow" }) {
				NodeList nodes = document.getElementsByTagName(tag);
				for (int i = 0; i < nodes.getLength(); i++) {
					String type = ((Element) nodes.item(i)).getAttribute("type");
					if (!known.contains(type) && !unknown.contains(type))
						unknown.add(type.isEmpty() ? "?" : type);
				}
			}
		} catch (Exception e) {
			return new Validation(false, cleaned, List.of("XML: " + e.getMessage()));
		}

		return new Validation(unknown.isEmpty(), cleaned, unknown);
	}

}
