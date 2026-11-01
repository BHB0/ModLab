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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.init.L10N;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Talks to an AI model chosen in the preferences: Gemini (free key from Google AI Studio) or a local Ollama server.
 * The key stays on this computer, in the preferences of ModLab.
 */
public final class AssistantClient {

	public record Message(String role, String text) {} // role: "user" or "assistant"

	public static boolean isConfigured() {
		var integrations = PreferencesManager.PREFERENCES.integrations;
		if ("Ollama".equals(integrations.aiProvider.get()))
			return !integrations.aiOllamaModel.get().isBlank() && !integrations.aiOllamaUrl.get().isBlank();
		return !integrations.aiApiKey.get().isBlank() && !integrations.aiModel.get().isBlank();
	}

	/**
	 * @return the answer of the model
	 * @throws IOException with a message that can be shown to the user
	 */
	public static String ask(String systemPrompt, List<Message> history) throws IOException, InterruptedException {
		if ("Ollama".equals(PreferencesManager.PREFERENCES.integrations.aiProvider.get()))
			return askOllama(systemPrompt, history);
		return askGemini(systemPrompt, history);
	}

	private static String askGemini(String systemPrompt, List<Message> history) throws IOException, InterruptedException {
		String model = PreferencesManager.PREFERENCES.integrations.aiModel.get().trim();
		String key = PreferencesManager.PREFERENCES.integrations.aiApiKey.get().trim();
		if (!model.matches("[A-Za-z0-9._-]+"))
			throw new IOException(L10N.t("assistant.error.model"));

		JsonObject body = new JsonObject();

		JsonObject system = new JsonObject();
		system.add("parts", parts(systemPrompt));
		body.add("system_instruction", system);

		JsonArray contents = new JsonArray();
		for (Message message : history) {
			JsonObject content = new JsonObject();
			content.addProperty("role", "assistant".equals(message.role()) ? "model" : "user");
			content.add("parts", parts(message.text()));
			contents.add(content);
		}
		body.add("contents", contents);

		JsonObject generation = new JsonObject();
		generation.addProperty("temperature", 0.4);
		body.add("generationConfig", generation);

		HttpRequest request = HttpRequest.newBuilder(
						URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
				.timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json")
				.header("x-goog-api-key", key).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();

		HttpResponse<String> response = send(request);
		if (response.statusCode() != 200)
			throw new IOException(describeError(response.statusCode(), response.body()));

		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonArray candidates = json.getAsJsonArray("candidates");
			if (candidates == null || candidates.isEmpty())
				throw new IOException(L10N.t("assistant.error.empty"));

			StringBuilder answer = new StringBuilder();
			JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
			if (content != null && content.has("parts")) {
				for (var part : content.getAsJsonArray("parts")) {
					if (part.getAsJsonObject().has("text"))
						answer.append(part.getAsJsonObject().get("text").getAsString());
				}
			}
			if (answer.isEmpty())
				throw new IOException(L10N.t("assistant.error.empty"));
			return answer.toString().strip();
		} catch (IllegalStateException | ClassCastException | NullPointerException e) {
			throw new IOException(L10N.t("assistant.error.empty"));
		}
	}

	private static String askOllama(String systemPrompt, List<Message> history) throws IOException, InterruptedException {
		String base = PreferencesManager.PREFERENCES.integrations.aiOllamaUrl.get().trim().replaceAll("/+$", "");
		String model = PreferencesManager.PREFERENCES.integrations.aiOllamaModel.get().trim();

		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("stream", false);

		JsonArray messages = new JsonArray();
		JsonObject system = new JsonObject();
		system.addProperty("role", "system");
		system.addProperty("content", systemPrompt);
		messages.add(system);
		for (Message message : history) {
			JsonObject entry = new JsonObject();
			entry.addProperty("role", message.role());
			entry.addProperty("content", message.text());
			messages.add(entry);
		}
		body.add("messages", messages);

		HttpRequest request;
		try {
			request = HttpRequest.newBuilder(URI.create(base + "/api/chat")).timeout(Duration.ofSeconds(240))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		} catch (IllegalArgumentException e) {
			throw new IOException(L10N.t("assistant.error.ollama_url"));
		}

		HttpResponse<String> response = send(request);
		if (response.statusCode() != 200)
			throw new IOException(describeError(response.statusCode(), response.body()));

		try {
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			return json.getAsJsonObject("message").get("content").getAsString().strip();
		} catch (IllegalStateException | ClassCastException | NullPointerException e) {
			throw new IOException(L10N.t("assistant.error.empty"));
		}
	}

	private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		try {
			return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
					.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new IOException(L10N.t("assistant.error.connection", String.valueOf(e.getMessage())));
		}
	}

	private static JsonArray parts(String text) {
		JsonArray parts = new JsonArray();
		JsonObject part = new JsonObject();
		part.addProperty("text", text);
		parts.add(part);
		return parts;
	}

	private static String describeError(int status, String body) {
		String detail = "";
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			if (json.has("error") && json.get("error").isJsonObject() && json.getAsJsonObject("error").has("message"))
				detail = json.getAsJsonObject("error").get("message").getAsString();
			else if (json.has("error"))
				detail = json.get("error").getAsString();
		} catch (Exception ignored) {
		}
		if (detail.isBlank())
			detail = body == null ? "" : body.strip();
		if (detail.length() > 300)
			detail = detail.substring(0, 300) + "...";

		String hint = switch (status) {
			case 400 -> L10N.t("assistant.error.http400");
			case 401, 403 -> L10N.t("assistant.error.http403");
			case 404 -> L10N.t("assistant.error.http404");
			case 429 -> L10N.t("assistant.error.http429");
			default -> "";
		};
		return (hint.isEmpty() ? "" : hint + "\n\n") + "HTTP " + status + (detail.isEmpty() ? "" : " : " + detail);
	}

}
