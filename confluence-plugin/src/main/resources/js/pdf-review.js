!function($){
$(document).ready(function(){

	var start_pdf_link = $('#start-pdf-review-from-file');
	start_pdf_link.click(function(e){
		e.preventDefault();
		start_pdf_dialog.show();
		loadui();
	});

	var start_pdf_dialog = new AJS.ConfluenceDialog({
	    width: 700,
	    height: 350,
	    id: "start-pdf-dialog"
	});
	start_pdf_dialog.addHeader("Start PDF Review from File", 'dialog-title');
	start_pdf_dialog.addButton("Reload", loadui, 'left');
	start_pdf_dialog.addSubmit("Start Review", function(dialog, page){
		start_pdf_dialog.addHeader("Uploading PDF and Creating Review...", 'dialog-title');
		start_pdf_dialog.disable();
		AJS.messages.generic("#pdfreview-notifications", {
			title: "Operation in progress.",
			body: "<p>The PDF file is being uploaded to the server and a review page is being created as requested.</p>"
		});
		$('#pdf-review-panel > form').submit();
		return true;
	});
	start_pdf_dialog.addCancel("Cancel", function(dialog, page){dialog.hide()});
	start_pdf_dialog.addPanel("Panel", "<div id='pdf-review-panel'></div>");

	var pdfreview_path_prefix = $.cookie('pdfreview-path-prefix') || 'Home/Non-Wiki Reviews';

	function setup_form(panel)
	{
		var form = panel.find('form');

		form.find('label').css('line-height','1.6');
		form.attr('action',start_pdf_link.attr('href'));

		var review_path = form.find('#reviewPath');

		function get_prefix_from_field()
		{
			var s = review_path.val();
			if (/\//.test(s))
				pdfreview_path_prefix = s.replace(/\/+[^\/]*$/,'');
			else
				pdfreview_path_prefix = '';
			return pdfreview_path_prefix;
		}

		var suggested_review_space = AJS.params.spaceKey;
		if (!/REV$/.test(suggested_review_space))
			suggested_review_space += 'REV';
		form.find('#reviewSpaceKey').val(suggested_review_space);
		form.find('#pdfcontent').change(function(){
			suggest_page_name_from_file(
					review_path,
					get_prefix_from_field(),
					$(this).val());
		});
		review_path.change(function(){
			$.cookie('pdfreview-path-prefix', get_prefix_from_field());
		})
		.val(pdfreview_path_prefix + (pdfreview_path_prefix?'/':'') + '...');
	}
	
	function suggest_page_name_from_file(page_input, prefix, filename)
	{
		filename = filename.replace(/^.*[\/\\]+/,'');
		filename = filename.replace(/\.[^.]*$/,'');
		filename = filename.replace(/[-_.]+/g,' ');
		filename = filename.replace(/^([a-z])/,function(s){return s.toUpperCase()});
		if (prefix === '')
			page_input.val(filename);
		else
			page_input.val(prefix + (/\/+$/.test(prefix)?'':'/') + filename);
	}
	
	function loadui(){
		var panel = $('#pdf-review-panel');
		$ .ajax({
			url: contextPath+'/download/resources/uk.co.jessamine.confluence.pdfreview/pdf-review-dialog.html',
			success: function(data){
				$(data).appendTo(panel.empty());
				setup_form(panel);
			},
			error: function(){
				$('<h3>Failed to load PDF Review UI</h3>').appendTo(panel.empty());
			},
			cache: false
		})
	};
});
}(AJS.$)
