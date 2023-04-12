#!/bin/bash

# execute before export my_passwd=xxxxxx

# Add SQLAlchemy
cd ~/py_venv
. raspi4_apps/bin/activate
pip install sqlalchemy>=2.0.0
cd ~/

# Enable webapp service
echo $my_passwd | { sudo --stdin cp ~/work/etc/default/webapp-healthcare /etc/default
  sudo cp ~/work/etc/systemd/system/webapp-healthcare.service /etc/systemd/system
  sudo systemctl enable webapp-healthcare.service
}

echo "rebooting."
echo $my_passwd |sudo --stdin reboot

