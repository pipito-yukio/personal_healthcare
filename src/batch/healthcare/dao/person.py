from sqlalchemy import String
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from dao import HealthcareBase

# https://docs.sqlalchemy.org/en/20/orm/quickstart.html
#  Declare Models
#
# https://stackoverflow.com/questions/19129289/how-to-define-composite-primary-key-in-sqlalchemy
#  How to define composite primary key in SQLAlchemy

# https://docs.sqlalchemy.org/en/14/orm/declarative_tables.html#orm-declarative-table-schema-name
# Table Configuration with Declarative
#  Explicit Schema Name with Declarative Table
#
# https://stackoverflow.com/questions/9298296/sqlalchemy-support-of-postgres-schemas
#  SQLAlchemy support of Postgres Schemas


class Person(HealthcareBase):
    __tablename__ = 'person'

    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[String] = mapped_column(String(50))
    name: Mapped[String] = mapped_column(String(24))

    def __repr__(self):
        return f"Person(id={self.id!r}, email={self.email!r}, name={self.name!r})"
