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

	/**
	 * @param variables may be {@code null} for a template that takes none. It
	 *                  used to be dereferenced unconditionally, so passing null
	 *                  threw an NPE from inside the renderer rather than at the
	 *                  call site -- guarded here instead of at each caller,
	 *                  since every caller would otherwise have to remember.
	 */
	public String render(String templateName, Map<String, Object> variables) {
		Context context = new Context();

		if (variables != null)
			variables.forEach(context::setVariable);

		return templateEngine.process(templateName, context);
	}

}
