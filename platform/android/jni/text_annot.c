#include "mupdf_native.h"

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_addMarkupAnnotationInternal)(JNIEnv * env, jobject thiz, jobjectArray points, enum pdf_annot_type type, jstring jtext)
{
    globals *glo = get_globals(env, thiz);
    if (glo == NULL) return;
    fz_context *ctx = glo->ctx;
    fz_document *doc = glo->doc;
    pdf_document *idoc = pdf_specifics(ctx, doc);
    page_cache *pc = &glo->pages[glo->current];
    jclass pt_cls;
    jfieldID x_fid, y_fid;
    int i, n;
    fz_point *pts = NULL;
    float color[3];
    float alpha;
    float line_height;
    float line_thickness;
    
    if (idoc == NULL)
        return;    
            
        switch (type)
        {
        case PDF_ANNOT_HIGHLIGHT:
            color[0] = glo->highlightColor[0];
            color[1] = glo->highlightColor[1];
            color[2] = glo->highlightColor[2];
            alpha = 0.69f; //HACK: Alphas smaller than 0.7 also get /BM Multiply in pdf_dev_alpha and so are displayed "behind" the text!
            line_thickness = 1.0;
            line_height = 0.5;
            break;
        case PDF_ANNOT_UNDERLINE:
            color[0] = glo->underlineColor[0];
            color[1] = glo->underlineColor[1];
            color[2] = glo->underlineColor[2];
            alpha = 1.0f;
            line_thickness = LINE_THICKNESS;
            line_height = UNDERLINE_HEIGHT;
            break;
        case PDF_ANNOT_STRIKE_OUT:
            color[0] = glo->strikeoutColor[0];
            color[1] = glo->strikeoutColor[1];
            color[2] = glo->strikeoutColor[2];
            alpha = 1.0f;
            line_thickness = LINE_THICKNESS;
            line_height = STRIKE_HEIGHT;
            break;
        case PDF_ANNOT_TEXT:
            color[0] = glo->textAnnotIconColor[0];
            color[1] = glo->textAnnotIconColor[1];
            color[2] = glo->textAnnotIconColor[2];
            alpha = 1.0f;
            break;
        case PDF_ANNOT_FREE_TEXT:
            color[0] = glo->inkColor[0];
            color[1] = glo->inkColor[1];
            color[2] = glo->inkColor[2];
            alpha = 1.0f;
            line_thickness = 0.0f;
            line_height = 0.0f;
            break;
        default:
            return;
    }

    fz_var(pts);
    fz_try(ctx)
    {
        pdf_annot *annot;
        fz_matrix ctm;

        float zoom = glo->resolution / 72;
        zoom = 1.0 / zoom;
        ctm = fz_scale(zoom, zoom);
//        pt_cls = (*env)->FindClass(env, "android.graphics.PointF");
        pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
        if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
        x_fid = (*env)->GetFieldID(env, pt_cls, "x", "F");
        if (x_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(x)");
        y_fid = (*env)->GetFieldID(env, pt_cls, "y", "F");
        if (y_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(y)");

        n = (*env)->GetArrayLength(env, points);

        pts = fz_malloc_array(ctx, n, fz_point);

        for (i = 0; i < n; i++)
        {
                //Fix the order of the points in the quad points of highlight annotations
            jobject opt;
            if(type == PDF_ANNOT_HIGHLIGHT)
                {
                if(i%4 == 2)
                    opt = (*env)->GetObjectArrayElement(env, points, i+1);
                else if(i%4 == 3)
                    opt = (*env)->GetObjectArrayElement(env, points, i-1);
                else
                    opt = (*env)->GetObjectArrayElement(env, points, i);
            }
            else
                opt = (*env)->GetObjectArrayElement(env, points, i);
            
            pts[i].x = opt ? (*env)->GetFloatField(env, opt, x_fid) : 0.0f;
            pts[i].y = opt ? (*env)->GetFloatField(env, opt, y_fid) : 0.0f;
            pts[i] = fz_transform_point(pts[i], ctm);
        }

        annot = pdf_create_annot(ctx, (pdf_page *)pc->page, type); // creates a simple annot without AP

            //Now we generate the AP:
        if(type == PDF_ANNOT_TEXT)
        {
                //Ensure order of points
            if(pts[0].x > pts[1].x)
            {
                float z = pts[1].x;
                pts[1].x = pts[0].x;
                pts[0].x = z;
            }
            if(pts[0].y > pts[1].y)
            {
                float z = pts[1].y;
                pts[1].y = pts[0].y;
                pts[0].y = z;
            }
            
            
            fz_rect rect = {pts[0].x, pts[0].y, pts[1].x, pts[1].y};

           const char *utf8 = (*env)->GetStringUTFChars(env, jtext, NULL);
           pdf_set_annot_rect(ctx, (pdf_annot *)annot, rect);
           if (utf8)
               pdf_set_annot_contents(ctx, (pdf_annot *)annot, utf8);
           
               //Generate an appearance stream (AP) for the annotation (this should only be done once for each document and then the relevant xobject just referenced...)
           const float linewidth = (pts[1].x - pts[0].x)*0.06;
           fz_matrix page_ctm = fz_identity;
           fz_display_list *dlist = NULL;
           fz_device *dev = NULL;
           fz_path *path = NULL;
           fz_stroke_state *stroke = NULL;
           
           fz_var(path);
           fz_var(stroke);
           fz_var(dev);
           fz_var(dlist);
           fz_try(ctx)
           {
               dlist = fz_new_display_list(ctx, rect);
               dev = fz_new_list_device(ctx, dlist);

               stroke = fz_new_stroke_state(ctx);
               stroke->linewidth = linewidth;
               const float halflinewidth = linewidth*0.5;
               path = fz_new_path(ctx);

               fz_moveto(ctx, path, pts[0].x, pts[1].y-halflinewidth);
               fz_lineto(ctx, path, pts[1].x-halflinewidth, pts[1].y-halflinewidth);
               fz_lineto(ctx, path, pts[1].x-halflinewidth, 0.8*pts[0].y+0.2*pts[1].y);
               fz_lineto(ctx, path, 0.3*pts[1].x+0.7*pts[0].x, 0.8*pts[0].y+0.2*pts[1].y);
               fz_lineto(ctx, path, pts[0].x+halflinewidth, pts[0].y+halflinewidth);
               fz_lineto(ctx, path, pts[0].x+halflinewidth, pts[1].y);

               
               fz_moveto(ctx, path, 0.8*pts[0].x+0.2*pts[1].x, 0.8*pts[1].y+0.2*pts[0].y-halflinewidth);
               fz_lineto(ctx, path, 0.2*pts[0].x+0.8*pts[1].x, 0.8*pts[1].y+0.2*pts[0].y-halflinewidth);
               fz_moveto(ctx, path, 0.8*pts[0].x+0.2*pts[1].x, 0.6*pts[1].y+0.4*pts[0].y);
               fz_lineto(ctx, path, 0.2*pts[0].x+0.8*pts[1].x, 0.6*pts[1].y+0.4*pts[0].y);
               fz_moveto(ctx, path, 0.8*pts[0].x+0.2*pts[1].x, 0.4*pts[1].y+0.6*pts[0].y+halflinewidth);
               fz_lineto(ctx, path, 0.4*pts[0].x+0.6*pts[1].x, 0.4*pts[1].y+0.6*pts[0].y+halflinewidth);
               
               fz_stroke_path(ctx, dev, path, stroke, page_ctm, fz_device_rgb(ctx), color, alpha, fz_default_color_params);
               rect = fz_transform_rect(rect, page_ctm);
               /* Set appearance from display list */
               pdf_set_annot_appearance_from_display_list(ctx, (pdf_annot *)annot, "N", NULL, fz_identity, dlist);
           }
           fz_always(ctx)
           {
               fz_drop_device(ctx, dev);
               fz_drop_display_list(ctx, dlist);
               fz_drop_stroke_state(ctx, stroke);
               fz_drop_path(ctx, path);

               if (utf8)
                   (*env)->ReleaseStringUTFChars(env, jtext, utf8);
           }
           fz_catch(ctx)
           {
               fz_rethrow(ctx);
           }
        } //Add a markup annotation
        else if (type == PDF_ANNOT_FREE_TEXT)
        {
            const char *utf8 = NULL;
            float minx = 0.0f, maxx = 0.0f, miny = 0.0f, maxy = 0.0f;
            fz_rect rect;
            float font_size;

            if (n >= 1)
            {
                minx = maxx = pts[0].x;
                miny = maxy = pts[0].y;
            }

            for (i = 1; i < n; ++i)
            {
                minx = fminf(minx, pts[i].x);
                maxx = fmaxf(maxx, pts[i].x);
                miny = fminf(miny, pts[i].y);
                maxy = fmaxf(maxy, pts[i].y);
            }

            if (maxx - minx < 16.0f)
            {
                float pad = 8.0f;
                minx -= pad;
                maxx += pad;
            }
            if (maxy - miny < 12.0f)
            {
                float pad = 12.0f - (maxy - miny);
                maxy += pad;
            }

            if (minx > maxx)
            {
                float tmp = minx;
                minx = maxx;
                maxx = tmp;
            }
            if (miny > maxy)
            {
                float tmp = miny;
                miny = maxy;
                maxy = tmp;
            }

            rect.x0 = minx;
            rect.x1 = maxx;
            rect.y0 = miny;
            rect.y1 = maxy;
            pdf_set_annot_rect(ctx, (pdf_annot *)annot, rect);

            if (jtext != NULL)
                utf8 = (*env)->GetStringUTFChars(env, jtext, NULL);

            pdf_set_annot_contents(ctx, (pdf_annot *)annot, utf8 ? utf8 : "");

            font_size = (rect.y1 - rect.y0) * 0.8f;
            font_size = fmaxf(10.0f, fminf(72.0f, font_size));
            pdf_set_annot_default_appearance(ctx, (pdf_annot *)annot, "Helv", font_size, 3, color);
            pdf_set_annot_border_width(ctx, (pdf_annot *)annot, 0.0f);
            pdf_set_annot_opacity(ctx, (pdf_annot *)annot, 1.0f);
            pdf_update_annot(ctx, (pdf_annot *)annot);

            if (utf8 != NULL)
                (*env)->ReleaseStringUTFChars(env, jtext, utf8);
        }
        else
        {
            if (n >= 4)
            {
                int qn = n/4;
                fz_quad *qv = fz_malloc_array(ctx, qn, fz_quad);
                int qi;
                for (qi = 0; qi < qn; ++qi)
                {
                    qv[qi].ul = pts[qi*4 + 0];
                    qv[qi].ur = pts[qi*4 + 1];
                    qv[qi].ll = pts[qi*4 + 2];
                    qv[qi].lr = pts[qi*4 + 3];
                }
                pdf_set_annot_quad_points(ctx, (pdf_annot *)annot, qn, qv);
                fz_free(ctx, qv);
            }
            pdf_set_annot_color(ctx, (pdf_annot *)annot, 3, color);
            pdf_set_annot_opacity(ctx, (pdf_annot *)annot, alpha);
        }
        
        /* Mark the annotation and page as dirty so exporters persist it. */
        pdf_dirty_annot(ctx, annot);
        dump_annotation_display_lists(glo);
        /* Mark page dirty so exports include the new annotation immediately. */
        pdf_update_page(ctx, (pdf_page *)pc->page);
    }
    fz_always(ctx)
    {
        fz_free(ctx, pts);
    }
    fz_catch(ctx)
    {
        LOGE("addMarkupAnnotationInternal: %s failed", fz_caught_message(ctx));
        jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if (cls != NULL)
            (*env)->ThrowNew(env, cls, "Out of memory in MuPDFCore_searchPage");
        (*env)->DeleteLocalRef(env, cls);
    }
}

JNIEXPORT void JNICALL
JNI_FN(MuPDFCore_deleteAnnotationInternal)(JNIEnv * env, jobject thiz, int annot_index)
{
	globals *glo = get_globals(env, thiz);
	if (glo == NULL) return;
	fz_context *ctx = glo->ctx;
	fz_document *doc = glo->doc;
	pdf_document *idoc = pdf_specifics(ctx, doc);
	page_cache *pc = &glo->pages[glo->current];
	pdf_annot *annot;
	int i;

	if (idoc == NULL)
		return;

	fz_try(ctx)
	{
			annot = pdf_first_annot(ctx, (pdf_page *)pc->page);
			for (i = 0; i < annot_index && annot; i++)
				annot = pdf_next_annot(ctx, annot);

		if (annot)
		{
			pdf_delete_annot(ctx, (pdf_page *)pc->page, annot);
			pdf_update_page(ctx, (pdf_page *)pc->page);
			dump_annotation_display_lists(glo);
		}
	}
	fz_catch(ctx)
	{
		LOGE("deleteAnnotationInternal: %s", fz_caught_message(ctx));
	}
}

JNIEXPORT jobjectArray JNICALL
JNI_FN(MuPDFCore_getAnnotationsInternal)(JNIEnv * env, jobject thiz, int pageNumber)
{
	jclass annotClass, pt_cls, ptarr_cls;
    jfieldID x_fid, y_fid;
    jmethodID Annotation;
    jmethodID PointF;
    jobjectArray arr;
    jobject jannot;
	pdf_annot *annot;
    fz_matrix ctm;
    float zoom;
    int count;
    page_cache *pc;
    globals *glo = get_globals(env, thiz);
    fz_context *ctx = glo->ctx;
    
    if (glo == NULL) return NULL;

    annotClass = (*env)->FindClass(env, PACKAGENAME "/Annotation");
    if (annotClass == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
    
    Annotation = (*env)->GetMethodID(env, annotClass, "<init>", "(FFFFI[[Landroid/graphics/PointF;Ljava/lang/String;J)V"); 
    if (Annotation == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetMethodID");

    pt_cls = (*env)->FindClass(env, "android/graphics/PointF");
    if (pt_cls == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "FindClass");
    x_fid = (*env)->GetFieldID(env, pt_cls, "x", "F");
    if (x_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(x)");
    y_fid = (*env)->GetFieldID(env, pt_cls, "y", "F");
    if (y_fid == NULL) fz_throw(ctx, FZ_ERROR_GENERIC, "GetFieldID(y)");
    PointF = (*env)->GetMethodID(env, pt_cls, "<init>", "(FF)V");
    
    JNI_FN(MuPDFCore_gotoPageInternal)(env, thiz, pageNumber);
    pc = &glo->pages[glo->current];
    if (pc->number != pageNumber || pc->page == NULL)
        return NULL;

    zoom = glo->resolution / 72;
    ctm = fz_scale(zoom, zoom);

    count = 0;
    for (pdf_annot *annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot))
        count ++;

    arr = (*env)->NewObjectArray(env, count, annotClass, NULL);
    if (arr == NULL) return NULL;

    count = 0;
    for (pdf_annot *annot = pdf_first_annot(ctx, (pdf_page*)pc->page); annot; annot = pdf_next_annot(ctx, annot))
    {
            //Get the type
        enum pdf_annot_type type = pdf_annot_type(ctx, annot);

            //Get the text of the annotation
        jstring jtext = NULL;
        if(type == PDF_ANNOT_TEXT || type == PDF_ANNOT_FREE_TEXT)
        {
            const char *text = pdf_annot_contents(ctx, (pdf_annot *)annot);
            if (text != NULL)
                jtext = (*env)->NewStringUTF(env, text);
        }

        
            //Get the inklist
        jobjectArray arcs = NULL;
    if(type == PDF_ANNOT_INK)
        {
            int nArcs = pdf_annot_ink_list_count(ctx, (pdf_annot *)annot);
            int i;
            for(i = 0; i < nArcs; i++)
            {
                int nArc = pdf_annot_ink_list_stroke_count(ctx, (pdf_annot *)annot, i);
                jobjectArray arci = (*env)->NewObjectArray(env, nArc, pt_cls, NULL);
                
                if(i==0) { //Get the class of the array of pointF and create the array of arrays 
                    ptarr_cls = (*env)->GetObjectClass(env, arci);
                    if (ptarr_cls == NULL) {
                        fz_throw(glo->ctx, FZ_ERROR_GENERIC, "GetObjectClass()");
                    }
                    else {
                        arcs = (*env)->NewObjectArray(env, nArcs, ptarr_cls, NULL);
                        if (arcs == NULL) fz_throw(glo->ctx, FZ_ERROR_GENERIC, "arcs == NULL");
                    }
                }
                
                if (arci == NULL) return NULL;
                int j;
                for(j = 0; j < nArc; j++)
                {
                    fz_point point = pdf_annot_ink_list_stroke_vertex(ctx, (pdf_annot *)annot, i, j);
                    point = fz_transform_point(point, ctm);
                    /* MuPDF ink vertices are returned in the same top-left page-pixel space
                     * used by the Android UI (matching draw/hit-testing coordinates). */
                    jobject pfobj = (*env)->NewObject(env, pt_cls, PointF, point.x, point.y);
                    (*env)->SetObjectArrayElement(env, arci, j, pfobj);
                    (*env)->DeleteLocalRef(env, pfobj);
                }
                (*env)->SetObjectArrayElement(env, arcs, i, arci);
                (*env)->DeleteLocalRef(env, arci);
            }
        }

            //Get the rect
        fz_rect rect;
        rect = pdf_bound_annot(ctx, (pdf_annot *)annot);
        rect = fz_transform_rect(rect, ctm);

            //Get a stable object identifier for undo/redo matching
        jlong objectNumber = -1;
        pdf_obj *annot_obj = pdf_annot_obj(ctx, (pdf_annot *)annot);
        if (annot_obj)
        {
            int num = pdf_to_num(ctx, annot_obj);
            int gen = pdf_to_gen(ctx, annot_obj);
            objectNumber = (((jlong)num) << 32) | (jlong)(gen & 0xffffffffu);
        }

            //Create the annotation
        if(Annotation != NULL)
        {
            jannot = (*env)->NewObject(env, annotClass, Annotation, (float)rect.x0, (float)rect.y0, (float)rect.x1, (float)rect.y1, type, arcs, jtext, objectNumber); 
        }
            
        if (jannot == NULL) return NULL;
        (*env)->SetObjectArrayElement(env, arr, count, jannot);

            //Clean up
        (*env)->DeleteLocalRef(env, jannot);
        (*env)->DeleteLocalRef(env, jtext);
        
        count ++;
    }

    return arr;
}
