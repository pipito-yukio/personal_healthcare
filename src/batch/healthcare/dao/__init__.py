from sqlalchemy import MetaData
from sqlalchemy.orm import declarative_base

# 健康管理データベース共通: PostgreSQL schema定義
schema = MetaData(schema="bodyhealth")
HealthcareBase = declarative_base(metadata=schema)
