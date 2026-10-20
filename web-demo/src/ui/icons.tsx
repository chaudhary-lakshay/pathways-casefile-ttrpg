// The Material icons the Android app draws (Icons.Filled.*), from the same
// Apache-2.0 set, inlined so they take currentColor.
import add from '@icons/add.svg?raw'
import arrowBack from '@icons/arrow_back.svg?raw'
import attachFile from '@icons/attach_file.svg?raw'
import autoFixHigh from '@icons/auto_fix_high.svg?raw'
import bolt from '@icons/bolt.svg?raw'
import casino from '@icons/casino.svg?raw'
import check from '@icons/check.svg?raw'
import church from '@icons/church.svg?raw'
import contentCopy from '@icons/content_copy.svg?raw'
import createNewFolder from '@icons/create_new_folder.svg?raw'
import description from '@icons/description.svg?raw'
import expandLess from '@icons/expand_less.svg?raw'
import expandMore from '@icons/expand_more.svg?raw'
import fingerprint from '@icons/fingerprint.svg?raw'
import heartBroken from '@icons/heart_broken.svg?raw'
import info from '@icons/info.svg?raw'
import keyboardArrowDown from '@icons/keyboard_arrow_down.svg?raw'
import keyboardArrowUp from '@icons/keyboard_arrow_up.svg?raw'
import menuBook from '@icons/menu_book.svg?raw'
import monitorHeart from '@icons/monitor_heart.svg?raw'
import moreVert from '@icons/more_vert.svg?raw'
import remove from '@icons/remove.svg?raw'
import science from '@icons/science.svg?raw'
import search from '@icons/search.svg?raw'
import sensors from '@icons/sensors.svg?raw'
import shield from '@icons/shield.svg?raw'
import visibility from '@icons/visibility.svg?raw'
import download from '@icons/download.svg?raw'
import upload from '@icons/upload.svg?raw'

export const ICONS = {
  add, arrowBack, attachFile, autoFixHigh, bolt, casino, check, church, contentCopy, createNewFolder,
  description, expandLess, expandMore, fingerprint, heartBroken, info, keyboardArrowDown, keyboardArrowUp,
  menuBook, monitorHeart, moreVert, remove, science, search, sensors, shield, visibility, download, upload,
}
export type IconName = keyof typeof ICONS

export function Icon({ name, size = 24, label, className }: { name: IconName; size?: number; label?: string; className?: string }) {
  return (
    <span
      className={`icon${className ? ` ${className}` : ''}`}
      style={{ width: size, height: size }}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      dangerouslySetInnerHTML={{ __html: ICONS[name] }}
    />
  )
}
