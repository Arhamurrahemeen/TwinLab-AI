from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    influx_url: str = "http://localhost:8086"
    influx_token: str = "twinlab-super-secret-token"
    influx_org: str = "twinlab"
    influx_bucket: str = "twinlab"
    mongo_uri: str = "mongodb://admin:twinlab123@localhost:27017"
    mongo_db: str = "twinlab"
    gemini_api_key: str = ""
    groq_api_key: str = ""
    twilio_account_sid: str = ""
    twilio_auth_token: str = ""
    twilio_whatsapp_from: str = ""
    alert_whatsapp_to: str = ""
    diesel_price_pkr: float = 280.0

    model_config = {"env_file": ".env"}


settings = Settings()
