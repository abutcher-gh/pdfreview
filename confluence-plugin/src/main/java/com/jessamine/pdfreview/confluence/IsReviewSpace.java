package com.jessamine.pdfreview.confluence;

import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.plugin.descriptor.web.WebInterfaceContext;
import com.atlassian.confluence.plugin.descriptor.web.conditions.BaseConfluenceCondition;
import com.atlassian.confluence.spaces.Space;

public class IsReviewSpace extends BaseConfluenceCondition {
	private final PageManager pageManager;

	public IsReviewSpace(PageManager pageManager) {
		this.pageManager = pageManager;
	}

	@Override
	protected boolean shouldDisplay(WebInterfaceContext context) {
		Space space = context.getSpace();
		if (space != null)
			return pageManager.getPage(space.getKey(), "Review Index") != null;
		return false;
	}
}
