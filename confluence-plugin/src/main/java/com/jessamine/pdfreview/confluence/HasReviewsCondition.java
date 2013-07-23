package com.jessamine.pdfreview.confluence;

import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.plugin.descriptor.web.WebInterfaceContext;
import com.atlassian.confluence.plugin.descriptor.web.conditions.BaseConfluenceCondition;

public class HasReviewsCondition extends BaseConfluenceCondition {
	private ContentPropertyManager contentPropertyManager;

	public HasReviewsCondition(ContentPropertyManager contentPropertyManager) {
		this.contentPropertyManager = contentPropertyManager;
	}

	@Override
	protected boolean shouldDisplay(WebInterfaceContext context) {
		AbstractPage page = context.getPage();
		if (page != null)
			return contentPropertyManager.getStringProperty(page,
					"pdfreview.lastReviewedOn") != null;
		return false;
	}
}
