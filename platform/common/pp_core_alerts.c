#include "pp_core_internal.h"

#include <string.h>

struct pp_pdf_alerts
{
	fz_context *ctx;
	pdf_document *pdf;
	int initialised;
	int alerts_active;
	int alert_request;
	int alert_reply;
	pdf_alert_event *current_alert;
	pthread_mutex_t fin_lock;
	pthread_mutex_t fin_lock2;
	pthread_mutex_t alert_lock;
	pthread_cond_t alert_request_cond;
	pthread_cond_t alert_reply_cond;
};

static void
pp_pdf_set_doc_event_callback_compat(fz_context *ctx, pdf_document *doc, pdf_doc_event_cb *event_cb, void *data)
{
#if PP_MUPDF_API_NEW
	pdf_set_doc_event_callback(ctx, doc, event_cb, NULL, data);
#else
	pdf_set_doc_event_callback(ctx, doc, event_cb, data);
#endif
}

static void
pp_pdf_show_alert(pp_pdf_alerts *alerts, pdf_alert_event *alert)
{
	if (!alerts || !alerts->initialised)
		return;

	pthread_mutex_lock(&alerts->fin_lock2);
	pthread_mutex_lock(&alerts->alert_lock);

	alert->button_pressed = 0;

	if (alerts->alerts_active)
	{
		alerts->current_alert = alert;
		alerts->alert_request = 1;
		pthread_cond_signal(&alerts->alert_request_cond);

		while (alerts->alerts_active && !alerts->alert_reply)
			pthread_cond_wait(&alerts->alert_reply_cond, &alerts->alert_lock);
		alerts->alert_reply = 0;
		alerts->current_alert = NULL;
	}

	pthread_mutex_unlock(&alerts->alert_lock);
	pthread_mutex_unlock(&alerts->fin_lock2);
}

static void
pp_pdf_alert_event_cb(fz_context *ctx, pdf_document *doc, pdf_doc_event *event, void *data)
{
	(void)ctx;
	(void)doc;
	pp_pdf_alerts *alerts = (pp_pdf_alerts *)data;

	if (!alerts || !event)
		return;

	switch (event->type)
	{
	case PDF_DOCUMENT_EVENT_ALERT:
		pp_pdf_show_alert(alerts, pdf_access_alert_event(alerts->ctx, event));
		break;
	}
}

pp_pdf_alerts *
pp_pdf_alerts_new_mupdf(void *mupdf_ctx, void *mupdf_doc)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	fz_document *doc = (fz_document *)mupdf_doc;
	pdf_document *pdf;
	pp_pdf_alerts *alerts = NULL;

	if (!ctx || !doc)
		return NULL;

	pdf = pdf_specifics(ctx, doc);
	if (!pdf)
		return NULL;

	fz_var(alerts);
	fz_try(ctx)
	{
		alerts = (pp_pdf_alerts *)fz_malloc(ctx, sizeof(pp_pdf_alerts));
		memset(alerts, 0, sizeof(*alerts));
		alerts->ctx = ctx;
		alerts->pdf = pdf;
		alerts->alerts_active = 0;
		alerts->alert_request = 0;
		alerts->alert_reply = 0;
		alerts->current_alert = NULL;
		pthread_mutex_init(&alerts->fin_lock, NULL);
		pthread_mutex_init(&alerts->fin_lock2, NULL);
		pthread_mutex_init(&alerts->alert_lock, NULL);
		pthread_cond_init(&alerts->alert_request_cond, NULL);
		pthread_cond_init(&alerts->alert_reply_cond, NULL);

		pdf_enable_js(ctx, pdf);
		pp_pdf_set_doc_event_callback_compat(ctx, pdf, pp_pdf_alert_event_cb, alerts);

		alerts->initialised = 1;
	}
	fz_catch(ctx)
	{
		if (alerts)
			fz_free(ctx, alerts);
		alerts = NULL;
	}

	return alerts;
}

void
pp_pdf_alerts_drop(pp_pdf_alerts *alerts)
{
	if (!alerts || !alerts->initialised)
		return;

	fz_context *ctx = alerts->ctx;

	/* Disable callbacks first to prevent new show_alert calls. */
	if (alerts->pdf)
		pp_pdf_set_doc_event_callback_compat(ctx, alerts->pdf, NULL, NULL);

	pthread_mutex_lock(&alerts->alert_lock);
	alerts->current_alert = NULL;
	alerts->alerts_active = 0;
	pthread_cond_signal(&alerts->alert_request_cond);
	pthread_cond_signal(&alerts->alert_reply_cond);
	pthread_mutex_unlock(&alerts->alert_lock);

	/* Wait for any in-flight wait/show calls. */
	pthread_mutex_lock(&alerts->fin_lock);
	pthread_mutex_unlock(&alerts->fin_lock);
	pthread_mutex_lock(&alerts->fin_lock2);
	pthread_mutex_unlock(&alerts->fin_lock2);

	pthread_cond_destroy(&alerts->alert_reply_cond);
	pthread_cond_destroy(&alerts->alert_request_cond);
	pthread_mutex_destroy(&alerts->alert_lock);
	pthread_mutex_destroy(&alerts->fin_lock2);
	pthread_mutex_destroy(&alerts->fin_lock);

	alerts->initialised = 0;
	fz_free(ctx, alerts);
}

int
pp_pdf_alerts_start(pp_pdf_alerts *alerts)
{
	if (!alerts || !alerts->initialised)
		return 0;
	pthread_mutex_lock(&alerts->alert_lock);
	alerts->alert_reply = 0;
	alerts->alert_request = 0;
	alerts->alerts_active = 1;
	alerts->current_alert = NULL;
	pthread_mutex_unlock(&alerts->alert_lock);
	return 1;
}

void
pp_pdf_alerts_stop(pp_pdf_alerts *alerts)
{
	if (!alerts || !alerts->initialised)
		return;
	pthread_mutex_lock(&alerts->alert_lock);
	alerts->alert_reply = 0;
	alerts->alert_request = 0;
	alerts->alerts_active = 0;
	alerts->current_alert = NULL;
	pthread_cond_signal(&alerts->alert_reply_cond);
	pthread_cond_signal(&alerts->alert_request_cond);
	pthread_mutex_unlock(&alerts->alert_lock);
}

int
pp_pdf_alerts_wait(pp_pdf_alerts *alerts, pp_pdf_alert *out_alert)
{
	pdf_alert_event alert;
	int alert_present;

	if (!alerts || !alerts->initialised || !out_alert)
		return 0;

	memset(out_alert, 0, sizeof(*out_alert));

	pthread_mutex_lock(&alerts->fin_lock);
	pthread_mutex_lock(&alerts->alert_lock);

	while (alerts->alerts_active && !alerts->alert_request)
		pthread_cond_wait(&alerts->alert_request_cond, &alerts->alert_lock);
	alerts->alert_request = 0;

	alert_present = (alerts->alerts_active && alerts->current_alert);
	if (alert_present)
		alert = *alerts->current_alert;

	pthread_mutex_unlock(&alerts->alert_lock);
	pthread_mutex_unlock(&alerts->fin_lock);

	if (!alert_present)
		return 0;

	/* Copy strings into MuPDF-managed memory so callers can outlive the callback window. */
	{
		int ok = 0;
		fz_try(alerts->ctx)
		{
			out_alert->title_utf8 = fz_strdup(alerts->ctx, alert.title ? alert.title : "");
			out_alert->message_utf8 = fz_strdup(alerts->ctx, alert.message ? alert.message : "");
			out_alert->icon_type = alert.icon_type;
			out_alert->button_group_type = alert.button_group_type;
			out_alert->button_pressed = alert.button_pressed;
			ok = 1;
		}
		fz_catch(alerts->ctx)
		{
			pp_pdf_alert_free_mupdf(alerts->ctx, out_alert);
			ok = 0;
		}
		return ok;
	}
}

void
pp_pdf_alerts_reply(pp_pdf_alerts *alerts, int button_pressed)
{
	if (!alerts || !alerts->initialised)
		return;

	pthread_mutex_lock(&alerts->alert_lock);
	if (alerts->alerts_active && alerts->current_alert)
	{
		alerts->current_alert->button_pressed = button_pressed;
		alerts->alert_reply = 1;
		pthread_cond_signal(&alerts->alert_reply_cond);
	}
	pthread_mutex_unlock(&alerts->alert_lock);
}

void
pp_pdf_alert_free_mupdf(void *mupdf_ctx, pp_pdf_alert *alert)
{
	fz_context *ctx = (fz_context *)mupdf_ctx;
	if (!ctx || !alert)
		return;
	if (alert->title_utf8)
		fz_free(ctx, alert->title_utf8);
	if (alert->message_utf8)
		fz_free(ctx, alert->message_utf8);
	memset(alert, 0, sizeof(*alert));
}

