import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from backend.config import settings


def send_password_reset_email(to_email: str, reset_link: str) -> None:
    """Send a password reset email without logging its sensitive link."""
    if not settings.SMTP_HOST:
        raise RuntimeError("SMTP is not configured")

    message = MIMEMultipart("alternative")
    message["Subject"] = "Reset your CloudBox password"
    message["From"] = settings.SMTP_FROM_EMAIL
    message["To"] = to_email
    message.attach(MIMEText(
        f"Use this link to reset your CloudBox password: {reset_link}", "plain"
    ))
    message.attach(MIMEText(
        "<p>Use the link below to reset your CloudBox password.</p>"
        f'<p><a href="{reset_link}">Reset password</a></p>'
        "<p>If you did not request this, you can ignore this email.</p>",
        "html",
    ))

    with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT) as server:
        if settings.SMTP_USE_TLS:
            server.starttls()
        if settings.SMTP_USERNAME:
            server.login(settings.SMTP_USERNAME, settings.SMTP_PASSWORD)
        server.sendmail(settings.SMTP_FROM_EMAIL, [to_email], message.as_string())
