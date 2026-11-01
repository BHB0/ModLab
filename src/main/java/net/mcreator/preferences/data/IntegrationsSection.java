/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2023, Pylo, opensource contributors
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

package net.mcreator.preferences.data;

import net.mcreator.preferences.PreferencesSection;
import net.mcreator.preferences.entries.BooleanEntry;
import net.mcreator.preferences.entries.IntegerEntry;
import net.mcreator.preferences.entries.StringEntry;

public class IntegrationsSection extends PreferencesSection {

	public final BooleanEntry discordRichPresenceEnable;
	public final BooleanEntry googleAnalyticsEnable;

	public final BooleanEntry mcpEnable;
	public final IntegerEntry mcpPort;

	public final StringEntry aiProvider;
	public final StringEntry aiApiKey;
	public final StringEntry aiModel;
	public final StringEntry aiOllamaUrl;
	public final StringEntry aiOllamaModel;

	public IntegrationsSection(String preferencesIdentifier) {
		super(preferencesIdentifier);

		discordRichPresenceEnable = addEntry(new BooleanEntry("discordRichPresenceEnable", true));
		googleAnalyticsEnable = addEntry(new BooleanEntry("googleAnalyticsEnable", false));
		mcpEnable = addEntry(new BooleanEntry("mcpEnable", false));
		mcpPort = addEntry(new IntegerEntry("mcpPort", 3025, 1024, 60000));

		// ModLab assistant
		aiProvider = addEntry(new StringEntry("aiProvider", "Gemini", "Gemini", "Ollama"));
		aiApiKey = addEntry(new StringEntry("aiApiKey", ""));
		aiModel = addEntry(new StringEntry("aiModel", "gemini-3.8-flash"));
		aiOllamaUrl = addEntry(new StringEntry("aiOllamaUrl", "http://localhost:11434"));
		aiOllamaModel = addEntry(new StringEntry("aiOllamaModel", "llama3.1"));
	}

	@Override public String getSectionKey() {
		return "integrations";
	}

}
