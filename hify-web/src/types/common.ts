export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface BaseModel {
  id?: number
  createdAt?: string
  updatedAt?: string
  deleted?: boolean
}
