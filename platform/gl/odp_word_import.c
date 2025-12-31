#include "odp_word_import.h"

#include "odp_state.h"

#include "mupdf/fitz.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32

int odp_word_import_to_cached_pdf(const char *word_path,
                                  char *out_pdf_path, size_t out_pdf_len,
                                  char *out_doc_id, size_t out_doc_id_len,
                                  char *out_err, size_t out_err_len)
{
	(void)word_path;
	if (out_pdf_path && out_pdf_len) out_pdf_path[0] = 0;
	if (out_doc_id && out_doc_id_len) out_doc_id[0] = 0;
	if (out_err && out_err_len) snprintf(out_err, out_err_len, "Word import is not supported on this platform.");
	return 0;
}

#else

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static void set_err(char *out_err, size_t out_err_len, const char *msg)
{
	if (!out_err || out_err_len == 0)
		return;
	snprintf(out_err, out_err_len, "%s", msg ? msg : "unknown error");
}

static void set_errno_err(char *out_err, size_t out_err_len, const char *prefix)
{
	if (!out_err || out_err_len == 0)
		return;
	snprintf(out_err, out_err_len, "%s: %s", prefix ? prefix : "error", strerror(errno));
}

static int has_suffix_case_insensitive(const char *s, const char *suffix)
{
	size_t ls, lsu;
	if (!s || !suffix) return 0;
	ls = strlen(s);
	lsu = strlen(suffix);
	if (ls < lsu) return 0;
	return fz_strcasecmp(s + (ls - lsu), suffix) == 0;
}

static int file_exists_nonempty(const char *path)
{
	struct stat st;
	if (!path || !*path)
		return 0;
	if (stat(path, &st) != 0)
		return 0;
	return S_ISREG(st.st_mode) && st.st_size > 0;
}

static void to_hex(const unsigned char *in, int in_len, char *out_hex, size_t out_hex_len)
{
	static const char digits[] = "0123456789abcdef";
	int i;
	if (!out_hex || out_hex_len == 0)
		return;
	if (out_hex_len < (size_t)(in_len * 2 + 1))
	{
		out_hex[0] = 0;
		return;
	}
	for (i = 0; i < in_len; ++i)
	{
		out_hex[i * 2 + 0] = digits[(in[i] >> 4) & 0xF];
		out_hex[i * 2 + 1] = digits[in[i] & 0xF];
	}
	out_hex[in_len * 2] = 0;
}

static void rm_rf(const char *path)
{
	struct stat st;
	DIR *dir;
	struct dirent *ent;

	if (!path || !*path)
		return;

	if (lstat(path, &st) != 0)
		return;

	if (S_ISDIR(st.st_mode))
	{
		dir = opendir(path);
		if (dir)
		{
			while ((ent = readdir(dir)) != NULL)
			{
				char child[2048];
				int wrote;

				if (strcmp(ent->d_name, ".") == 0 || strcmp(ent->d_name, "..") == 0)
					continue;

				wrote = snprintf(child, sizeof(child), "%s/%s", path, ent->d_name);
				if (wrote > 0 && wrote < (int)sizeof(child))
					rm_rf(child);
			}
			closedir(dir);
		}
		(void)rmdir(path);
	}
	else
	{
		(void)unlink(path);
	}
}

static int run_soffice_convert(const char *input_path, const char *out_dir, const char *profile_dir,
                               const char *log_path, int timeout_sec,
                               char *out_err, size_t out_err_len)
{
	char profile_arg[2048];
	char *argv[32];
	pid_t pid;
	int status;
	time_t start;
	int log_fd = -1;

	if (!input_path || !out_dir || !profile_dir)
	{
		set_err(out_err, out_err_len, "internal error: missing args");
		return 0;
	}

	if (snprintf(profile_arg, sizeof(profile_arg), "-env:UserInstallation=file://%s", profile_dir) >= (int)sizeof(profile_arg))
	{
		set_err(out_err, out_err_len, "profile path too long");
		return 0;
	}

	argv[0] = (char *)"soffice";
	argv[1] = (char *)"--headless";
	argv[2] = (char *)"--nologo";
	argv[3] = (char *)"--nolockcheck";
	argv[4] = (char *)"--nodefault";
	argv[5] = (char *)"--norestore";
	argv[6] = (char *)"--invisible";
	argv[7] = profile_arg;
	argv[8] = (char *)"--convert-to";
	argv[9] = (char *)"pdf";
	argv[10] = (char *)"--outdir";
	argv[11] = (char *)out_dir;
	argv[12] = (char *)input_path;
	argv[13] = NULL;

	if (log_path && *log_path)
	{
		log_fd = open(log_path, O_CREAT | O_TRUNC | O_WRONLY, 0644);
		if (log_fd < 0)
			log_fd = -1;
	}

	pid = fork();
	if (pid < 0)
	{
		if (log_fd >= 0) close(log_fd);
		set_errno_err(out_err, out_err_len, "fork");
		return 0;
	}
	if (pid == 0)
	{
		if (log_fd >= 0)
		{
			dup2(log_fd, STDOUT_FILENO);
			dup2(log_fd, STDERR_FILENO);
		}
		execvp(argv[0], argv);
		fprintf(stderr, "execvp(soffice) failed: %s\n", strerror(errno));
		_exit(127);
	}

	if (log_fd >= 0) close(log_fd);

	start = time(NULL);
	for (;;)
	{
		pid_t r = waitpid(pid, &status, WNOHANG);
		if (r == pid)
			break;
		if (r == 0)
		{
			if (timeout_sec > 0 && time(NULL) - start > timeout_sec)
			{
				kill(pid, SIGKILL);
				waitpid(pid, &status, 0);
				set_err(out_err, out_err_len, "LibreOffice conversion timed out");
				return 0;
			}
			usleep(100 * 1000);
			continue;
		}
		if (r < 0 && errno == EINTR)
			continue;
		if (r < 0)
		{
			set_errno_err(out_err, out_err_len, "waitpid");
			return 0;
		}
	}

	if (!WIFEXITED(status) || WEXITSTATUS(status) != 0)
	{
		char msg[256];
		snprintf(msg, sizeof(msg), "LibreOffice conversion failed (exit=%d)", WIFEXITED(status) ? WEXITSTATUS(status) : -1);
		set_err(out_err, out_err_len, msg);
		return 0;
	}

	return 1;
}

int odp_word_import_to_cached_pdf(const char *word_path,
                                  char *out_pdf_path, size_t out_pdf_len,
                                  char *out_doc_id, size_t out_doc_id_len,
                                  char *out_err, size_t out_err_len)
{
	char cache_dir[2048];
	char word_dir[2048];
	char tmp_dir[2048];
	char profile_dir[2048];
	char input_tmp_template[2048];
	char input_named[2048];
	char pdf_out[2048];
	char log_path[2048];
	char hex[65];
	FILE *in = NULL;
	FILE *out = NULL;
	int ok = 0;
	const char *ext = ".docx";
	int timeout_sec = 120;
	fz_sha256 sha;
	unsigned char digest[32];

	if (out_pdf_path && out_pdf_len) out_pdf_path[0] = 0;
	if (out_doc_id && out_doc_id_len) out_doc_id[0] = 0;
	if (out_err && out_err_len) out_err[0] = 0;

	if (!word_path || !*word_path)
	{
		set_err(out_err, out_err_len, "no input path");
		return 0;
	}

	if (odp_cache_dir(cache_dir, sizeof(cache_dir)) != 0)
	{
		set_err(out_err, out_err_len, "failed to resolve cache dir");
		return 0;
	}

	if (snprintf(word_dir, sizeof(word_dir), "%s/word", cache_dir) >= (int)sizeof(word_dir))
	{
		set_err(out_err, out_err_len, "cache dir path too long");
		return 0;
	}
	if (mkdir(word_dir, 0755) != 0 && errno != EEXIST)
	{
		set_errno_err(out_err, out_err_len, "mkdir(word cache dir)");
		return 0;
	}

	if (has_suffix_case_insensitive(word_path, ".doc"))
		ext = ".doc";
	else if (has_suffix_case_insensitive(word_path, ".docx"))
		ext = ".docx";

	in = fopen(word_path, "rb");
	if (!in)
	{
		set_errno_err(out_err, out_err_len, "open input");
		return 0;
	}

	if (snprintf(tmp_dir, sizeof(tmp_dir), "%s/word_import_XXXXXX", cache_dir) >= (int)sizeof(tmp_dir))
	{
		fclose(in);
		set_err(out_err, out_err_len, "tmp dir path too long");
		return 0;
	}
	if (!mkdtemp(tmp_dir))
	{
		fclose(in);
		set_errno_err(out_err, out_err_len, "mkdtemp");
		return 0;
	}

	if (snprintf(profile_dir, sizeof(profile_dir), "%s/lo_profile", tmp_dir) >= (int)sizeof(profile_dir))
	{
		fclose(in);
		set_err(out_err, out_err_len, "profile dir path too long");
		goto cleanup;
	}
	if (mkdir(profile_dir, 0755) != 0 && errno != EEXIST)
	{
		fclose(in);
		set_errno_err(out_err, out_err_len, "mkdir(profile)");
		goto cleanup;
	}

	if (snprintf(input_tmp_template, sizeof(input_tmp_template), "%s/input_XXXXXX", tmp_dir) >= (int)sizeof(input_tmp_template))
	{
		fclose(in);
		set_err(out_err, out_err_len, "temp input path too long");
		goto cleanup;
	}

	{
		int fd = mkstemp(input_tmp_template);
		if (fd < 0)
		{
			fclose(in);
			set_errno_err(out_err, out_err_len, "mkstemp");
			goto cleanup;
		}
		out = fdopen(fd, "wb");
		if (!out)
		{
			close(fd);
			fclose(in);
			set_errno_err(out_err, out_err_len, "fdopen");
			goto cleanup;
		}
	}

	{
		unsigned char buf[32 * 1024];
		size_t r;
		fz_sha256_init(&sha);
		fz_sha256_update(&sha, (const unsigned char *)"odpdf-docid-v1", (unsigned int)strlen("odpdf-docid-v1"));
		while ((r = fread(buf, 1, sizeof(buf), in)) > 0)
		{
			fz_sha256_update(&sha, buf, (unsigned int)r);
			if (fwrite(buf, 1, r, out) != r)
			{
				fclose(in);
				fclose(out);
				set_errno_err(out_err, out_err_len, "write temp input");
				goto cleanup;
			}
		}
		if (ferror(in))
		{
			fclose(in);
			fclose(out);
			set_errno_err(out_err, out_err_len, "read input");
			goto cleanup;
		}
	}

	fz_sha256_final(&sha, digest);
	to_hex(digest, (int)sizeof(digest), hex, sizeof(hex));
	if (!hex[0])
	{
		set_err(out_err, out_err_len, "failed to hash input");
		goto cleanup;
	}

	fclose(in);
	in = NULL;
	if (fclose(out) != 0)
	{
		out = NULL;
		set_errno_err(out_err, out_err_len, "close temp input");
		goto cleanup;
	}
	out = NULL;

	if (out_doc_id && out_doc_id_len)
	{
		if (snprintf(out_doc_id, out_doc_id_len, "sha256:%s", hex) >= (int)out_doc_id_len)
			out_doc_id[0] = 0;
	}

	if (snprintf(input_named, sizeof(input_named), "%s/%s%s", tmp_dir, hex, ext) >= (int)sizeof(input_named))
	{
		set_err(out_err, out_err_len, "named temp input path too long");
		goto cleanup;
	}
	if (rename(input_tmp_template, input_named) != 0)
	{
		set_errno_err(out_err, out_err_len, "rename temp input");
		goto cleanup;
	}
	input_tmp_template[0] = 0;

	if (snprintf(pdf_out, sizeof(pdf_out), "%s/%s.pdf", word_dir, hex) >= (int)sizeof(pdf_out))
	{
		set_err(out_err, out_err_len, "output PDF path too long");
		goto cleanup;
	}

	if (!file_exists_nonempty(pdf_out))
	{
		if (snprintf(log_path, sizeof(log_path), "%s/%s.log", word_dir, hex) >= (int)sizeof(log_path))
			log_path[0] = 0;

		if (!run_soffice_convert(input_named, word_dir, profile_dir, log_path, timeout_sec, out_err, out_err_len))
			goto cleanup;

		if (!file_exists_nonempty(pdf_out))
		{
			set_err(out_err, out_err_len, "LibreOffice conversion succeeded but output PDF is missing/empty");
			goto cleanup;
		}
	}

	if (out_pdf_path && out_pdf_len)
	{
		if (snprintf(out_pdf_path, out_pdf_len, "%s", pdf_out) >= (int)out_pdf_len)
		{
			set_err(out_err, out_err_len, "output path too long");
			goto cleanup;
		}
	}

	ok = 1;

cleanup:
	if (in) fclose(in);
	if (out) fclose(out);
	if (input_named[0]) unlink(input_named);
	if (input_tmp_template[0]) unlink(input_tmp_template);
	if (tmp_dir[0]) rm_rf(tmp_dir);
	return ok;
}

#endif
