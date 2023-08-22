from logging import Logger
from typing import Optional, Tuple

from sqlalchemy.engine import Result
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import scoped_session
from sqlalchemy.sql import text

"""
メールアドレスに対応するユーザの測定開始日と測定最終日を検索する
※メールアドレスは事前にチェック済みであること
"""


class UserRegisterDays:
    _QUERY: str = """
SELECT
  to_char(min(measurement_day),'YYYY-MM-DD') as first_day
  , to_char(max(measurement_day),'YYYY-MM-DD') as last_day
FROM
  bodyhealth.person p
  INNER JOIN bodyhealth.sleep_management sm ON p.id = sm.pid
WHERE
  email=:emailAddress
"""

    def __init__(self, sess: scoped_session, email_address: str,
                 logger: Logger = None):
        # 健康管理DB用セッションオブジェクト
        self.sess: scoped_session = sess
        self.email_address: str = email_address
        self.logger = logger

    def get_days(self) -> Optional[Tuple[str, str]]:
        params = {"emailAddress": self.email_address}
        try:
            rs: Result = self.sess.execute(text(self._QUERY), params)
            row = None
            if rs:
                row = rs.fetchone()
            self.sess.commit()
            if row is None:
                return None

            first_day: str = row[0]
            last_day: str = row[1]
        except SQLAlchemyError as err:
            self.sess.rollback()
            if self.logger:
                self.logger.warning(err.args)
            return None
        finally:
            self.sess.close()

        return first_day, last_day
