package com.jessamine.pdfreview.confluence;

import java.util.Map;

import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;

public class ReviewIndex extends BaseMacro {
	private SettingsManager settingsManager;
	private PageManager pageManager;
	private ContentPropertyManager contentPropertyManager;

	ReviewIndex(SettingsManager settingsManager, PageManager pageManager,
			AttachmentManager attachmentManager, SpaceManager spaceManager,
			ContentPropertyManager contentPropertyManager) {
		this.settingsManager = settingsManager;
		this.pageManager = pageManager;
		this.contentPropertyManager = contentPropertyManager;
	}

	public boolean isInline() {
		return false;
	}

	public boolean hasBody() {
		return false;
	}

	public RenderMode getBodyRenderMode() {
		return RenderMode.NO_RENDER;
	}

	public String execute(@SuppressWarnings("rawtypes") Map params, String body, RenderContext renderContext)
			throws MacroException {
		PageContext pageContext = (PageContext) renderContext;
		Page page = (Page) pageContext.getEntity();

		{
			String pageOverride = (String) params.get("page");
			if (pageOverride != null) {
				Page p = pageManager.getPage(page.getSpaceKey(), pageOverride);
				if (p != null)
					page = p;
			}
		}

		StringBuilder rc = new StringBuilder();

		String maxId = contentPropertyManager.getStringProperty(page,
				"pdfreview.maxId");

		if (maxId == null || maxId.isEmpty())
			return "<p><strong>Error:</strong> no review index created here.</p>";

		int max = Integer.parseInt(maxId);

		rc.append("<p><ol>");
		for (int i = max; i > 0; --i) {
			String id = Integer.toString(i);

			rc.append("<li value='" + id + "'>");

			try {
				String head;
				String pageId = contentPropertyManager.getStringProperty(page,
						"pdfreview." + id + ".page");
				AbstractPage p = pageManager.getPage(Long.parseLong(pageId));
				if (p != null) {
					p = p.getLatestVersion();
					head = "<a href=\""
							+ settingsManager.getGlobalSettings().getBaseUrl()
							+ p.getUrlPath() + "#"
							+ p.getTitle().replaceAll("[ +]", "") + "-" + id
							+ "\">" + p.getTitle() + "</a>";
				} else {
					head = pageId;
				}

				String owner = contentPropertyManager.getStringProperty(page,
						"pdfreview." + id + ".owner");
				String created = contentPropertyManager.getStringProperty(page,
						"pdfreview." + id + ".created");

				rc.append("<strong>" + head + "</strong>");
				rc.append("<div style='font-size: 80%'>");
				rc.append("<strong>Created:</strong> " + created + " (" + owner
						+ ")<br/>");
				rc.append("<strong>Changelog:</strong> "
						+ contentPropertyManager.getTextProperty(page,
								"pdfreview." + id + ".state") + "<br/>\n");
				rc.append("<strong>Status:</strong> "
						+ ReviewStatus.codeToString(ReviewStatus.getStatus(
								contentPropertyManager, page, id)) + "<br/>\n");
			} catch (Exception e) {
				rc.append("<strong>Error:</strong> " + e.toString() + "<br/>\n");
			}
			rc.append("</div></li>");
		}
		rc.append("</ol></p>");

		return rc.toString();
	}
}
