#include "mupdf_native.h"

JNIEXPORT void JNICALL JNI_FN(MuPDFCore_gotoPageInternal)(JNIEnv *env, jobject thiz, int page);

static void update_changed_rects(globals *glo, page_cache *pc, pdf_document *idoc)
{
    fz_context *ctx = glo->ctx;
    (void)idoc;
    /* New MuPDF updates page appearance in one call; skip polling changed annots. */
    pdf_update_page(ctx, (pdf_page *)pc->page);
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_drawPage)(JNIEnv *env, jobject thiz, jobject bitmap,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, jlong cookiePtr)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret;
	fz_device *dev = NULL;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	fz_rect rect;
	fz_pixmap *pix = NULL;
	float xscale, yscale;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	page_cache *pc = &glo->pages[glo->current];
	int hq = (patchW < pageW || patchH < pageH);
	fz_matrix scale;
	fz_cookie *cookie = (fz_cookie *)(intptr_t)cookiePtr;

	if (pc->page == NULL)
		return 0;

	fz_var(pix);
	fz_var(dev);

	LOGI("In native method\n");
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	LOGI("Checking format\n");
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	LOGI("locking pixels\n");
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	/* Call mupdf to render display list to screen */
	LOGI("Rendering page(%d)=%dx%d patch=[%d,%d,%d,%d]",
			pc->number, pageW, pageH, patchX, patchY, patchW, patchH);

	fz_try(ctx)
	{
		fz_irect pixbbox;
		pdf_document *idoc = pdf_specifics(ctx, doc);

		if (idoc)
		{
			/* Update the changed-rects for both hq patch and main bitmap */
			update_changed_rects(glo, pc, idoc);

			/* Then drop the changed-rects for the bitmap we're about to
			render because we are rendering the entire area */
			drop_changed_rects(ctx, hq ? &pc->hq_changed_rects : &pc->changed_rects);
		}

            if (pc->page_list == NULL)
            {
                /* Render to list */
                pc->page_list = fz_new_display_list(ctx, pc->media_box);
                dev = fz_new_list_device(ctx, pc->page_list);

                LOGI("native draw_page() with cookie=%ld", (long)(intptr_t)cookie);
                if (cookie != NULL && !cookie->abort)
                    fz_run_page_contents(ctx, pc->page, dev, fz_identity, cookie);
                fz_drop_device(ctx, dev);
                dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->page_list);
				pc->page_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}
            if (pc->annot_list == NULL)
            {
                pdf_annot *annot;
                pc->annot_list = fz_new_display_list(ctx, pc->media_box);
                dev = fz_new_list_device(ctx, pc->annot_list);
                for (annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot)) 
                {
                    if (cookie == NULL || cookie->abort)
                        break;
                    pdf_run_annot(ctx, annot, dev, fz_identity, cookie);
                }
                fz_drop_device(ctx, dev);
                dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->annot_list);
				pc->annot_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}
		bbox.x0 = patchX;
		bbox.y0 = patchY;
		bbox.x1 = patchX + patchW;
		bbox.y1 = patchY + patchH;
		pixbbox = bbox;
		pixbbox.x1 = pixbbox.x0 + info.width;
		/* pixmaps cannot handle right-edge padding, so the bbox must be expanded to
		 * match the pixels data */
		pix = fz_new_pixmap_with_bbox_and_data(ctx, glo->colorspace, pixbbox, NULL, 1, (unsigned char *)pixels);
		if (pc->page_list == NULL && pc->annot_list == NULL)
		{
			fz_clear_pixmap_with_value(ctx, pix, 0xd0);
			break;
		}
		fz_clear_pixmap_with_value(ctx, pix, 0xff);

		zoom = glo->resolution / 72;
                ctm = fz_scale(zoom, zoom);
		rect = pc->media_box;
		bbox = fz_round_rect(fz_transform_rect(rect, ctm));
		/* Now, adjust ctm so that it would give the correct page width
		 * heights. */
		xscale = (float)pageW/(float)(bbox.x1-bbox.x0);
		yscale = (float)pageH/(float)(bbox.y1-bbox.y0);
                ctm = fz_pre_scale(ctm, xscale, yscale);
		rect = pc->media_box;
                rect = fz_transform_rect(rect, ctm);
                dev = fz_new_draw_device(ctx, fz_identity, pix);
#ifdef TIME_DISPLAY_LIST
		{
			clock_t time;
			int i;

			LOGI("Executing display list");
			time = clock();
			for (i=0; i<100;i++) {
#endif
                        if (pc->page_list)
                            fz_run_display_list(ctx, pc->page_list, dev, ctm, rect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

                        if (pc->annot_list)
                            fz_run_display_list(ctx, pc->annot_list, dev, ctm, rect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

#ifdef TIME_DISPLAY_LIST
			}
			time = clock() - time;
			LOGI("100 renders in %d (%d per sec)", time, CLOCKS_PER_SEC);
		}
#endif
		fz_drop_device(ctx, dev);
		dev = NULL;
		fz_drop_pixmap(ctx, pix);
		LOGI("Rendered");
	}
	fz_always(ctx)
	{
		fz_drop_device(ctx, dev);
		dev = NULL;
	}
	fz_catch(ctx)
	{
		LOGE("Render failed");
	}

	AndroidBitmap_unlockPixels(env, bitmap);

	return 1;
}

static char *widget_type_string(int t)
{
	switch(t)
	{
    case PDF_WIDGET_TYPE_BUTTON: return "pushbutton";
	case PDF_WIDGET_TYPE_CHECKBOX: return "checkbox";
	case PDF_WIDGET_TYPE_RADIOBUTTON: return "radiobutton";
	case PDF_WIDGET_TYPE_TEXT: return "text";
	case PDF_WIDGET_TYPE_LISTBOX: return "listbox";
	case PDF_WIDGET_TYPE_COMBOBOX: return "combobox";
	case PDF_WIDGET_TYPE_SIGNATURE: return "signature";
	default: return "non-widget";
	}
}

JNIEXPORT jboolean JNICALL
JNI_FN(MuPDFCore_updatePageInternal)(JNIEnv *env, jobject thiz, jobject bitmap, int page,
		int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, jlong cookiePtr)
{
	AndroidBitmapInfo info;
	void *pixels;
	int ret;
	fz_device *dev = NULL;
	float zoom;
	fz_matrix ctm;
	fz_irect bbox;
	fz_rect rect;
	fz_pixmap *pix = NULL;
	float xscale, yscale;
	pdf_document *idoc;
	page_cache *pc = NULL;
	int hq = (patchW < pageW || patchH < pageH);
	int i;
	globals *glo = get_globals(env, thiz);
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	rect_node *crect;
	fz_matrix scale;
	fz_cookie *cookie = (fz_cookie *)(intptr_t)cookiePtr;

	for (i = 0; i < NUM_CACHE; i++)
	{
		if (glo->pages[i].page != NULL && glo->pages[i].number == page)
		{
			pc = &glo->pages[i];
			break;
		}
	}

	if (pc == NULL)
	{
		/* Without a cached page object we cannot perform a partial update so
		render the entire bitmap instead */
		JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, page);
		return JNI_FN(MuPDFCore_drawPage)(env, thiz, bitmap, pageW, pageH, patchX, patchY, patchW, patchH, (jlong)(intptr_t)cookie);
	}

	idoc = pdf_specifics(ctx, doc);

	fz_var(pix);
	fz_var(dev);

	LOGI("In native method\n");
	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return 0;
	}

	LOGI("Checking format\n");
	if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Bitmap format is not RGBA_8888 !");
		return 0;
	}

	LOGI("locking pixels\n");
	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		return 0;
	}

	/* Call mupdf to render display list to screen */
	LOGI("Rendering page(%d)=%dx%d patch=[%d,%d,%d,%d]",
			pc->number, pageW, pageH, patchX, patchY, patchW, patchH);

	fz_try(ctx)
	{
			pdf_annot *annot;
		fz_irect pixbbox;

		if (idoc)
		{
			/* Update the changed-rects for both hq patch and main bitmap */
			update_changed_rects(glo, pc, idoc);
		}

                if (pc->page_list == NULL)
                {
                    /* Render to list */
                    pc->page_list = fz_new_display_list(ctx, pc->media_box);
                    dev = fz_new_list_device(ctx, pc->page_list);
			fz_run_page_contents(ctx, pc->page, dev, fz_identity, cookie);
			fz_drop_device(ctx, dev);
			dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->page_list);
				pc->page_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}

                if (pc->annot_list == NULL) {
                    pc->annot_list = fz_new_display_list(ctx, pc->media_box);
                    dev = fz_new_list_device(ctx, pc->annot_list);
                    for (pdf_annot *annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot))
                        pdf_run_annot(ctx, annot, dev, fz_identity, cookie);
			fz_drop_device(ctx, dev);
			dev = NULL;
			if (cookie != NULL && cookie->abort)
			{
				fz_drop_display_list(ctx, pc->annot_list);
				pc->annot_list = NULL;
				fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");
			}
		}

		bbox.x0 = patchX;
		bbox.y0 = patchY;
		bbox.x1 = patchX + patchW;
		bbox.y1 = patchY + patchH;
		pixbbox = bbox;
		pixbbox.x1 = pixbbox.x0 + info.width;
		/* pixmaps cannot handle right-edge padding, so the bbox must be expanded to
		 * match the pixels data */
		pix = fz_new_pixmap_with_bbox_and_data(ctx, glo->colorspace, pixbbox, NULL, 1, (unsigned char *)pixels);

		zoom = glo->resolution / 72;
            ctm = fz_scale(zoom, zoom);
            rect = pc->media_box;
            bbox = fz_round_rect(fz_transform_rect(rect, ctm));
		/* Now, adjust ctm so that it would give the correct page width
		 * heights. */
		xscale = (float)pageW/(float)(bbox.x1-bbox.x0);
		yscale = (float)pageH/(float)(bbox.y1-bbox.y0);
            ctm = fz_pre_scale(ctm, xscale, yscale);
            rect = pc->media_box;
            rect = fz_transform_rect(rect, ctm);

		LOGI("Start partial update");
		for (crect = hq ? pc->hq_changed_rects : pc->changed_rects; crect; crect = crect->next)
		{
			fz_irect abox;
                fz_rect arect = crect->rect;
                arect = fz_transform_rect(arect, ctm);
                arect = fz_intersect_rect(arect, rect);
                abox = fz_round_rect(arect);

			LOGI("Update rectangle (%d, %d, %d, %d)", abox.x0, abox.y0, abox.x1, abox.y1);
                if (!fz_is_empty_irect(abox))
			{
				LOGI("And it isn't empty");
                    fz_clear_pixmap_rect_with_value(ctx, pix, 0xff, abox);
                dev = fz_new_draw_device_with_bbox(ctx, fz_identity, pix, &abox);
				if (pc->page_list)
                    fz_run_display_list(ctx, pc->page_list, dev, ctm, arect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

				if (pc->annot_list)
                    fz_run_display_list(ctx, pc->annot_list, dev, ctm, arect, cookie);
				if (cookie != NULL && cookie->abort)
					fz_throw(ctx, FZ_ERROR_GENERIC, "Render aborted");

				fz_drop_device(ctx, dev);
				dev = NULL;
			}
		}
		LOGI("End partial update");

		/* Drop the changed rects we've just rendered */
		drop_changed_rects(ctx, hq ? &pc->hq_changed_rects : &pc->changed_rects);

		LOGI("Rendered");
	}
	fz_always(ctx)
	{
		fz_drop_device(ctx, dev);
		dev = NULL;
	}
	fz_catch(ctx)
	{
		LOGE("Render failed");
	}

	fz_drop_pixmap(ctx, pix);
	AndroidBitmap_unlockPixels(env, bitmap);

	return 1;
}
