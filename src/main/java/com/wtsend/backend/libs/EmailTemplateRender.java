package com.wtsend.backend.libs;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailTemplateRender {
	private final TemplateEngine templateEngine;

	public String render(String templateName, Map<String, Object> variables) {
		Context context = new Context();

		variables.forEach(context::setVariable);

		return templateEngine.process(templateName, context);
	}

}
